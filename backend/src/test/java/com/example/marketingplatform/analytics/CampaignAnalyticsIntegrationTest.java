package com.example.marketingplatform.analytics;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.analytics.application.CampaignAnalyticsIntegrityException;
import com.example.marketingplatform.analytics.application.CampaignAnalyticsService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.identity.infrastructure.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignAnalyticsIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID MEMBER = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID OUTSIDE_MEMBER = UUID.fromString("20000000-0000-4000-8000-000000000002");
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CampaignAnalyticsService service;
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtTokenService tokens;
    private UUID campaign;
    private UUID message;
    private UUID coupon;
    private UUID couponTwo;
    private UUID couponThree;

    @BeforeEach
    void setup() {
        campaign = UUID.randomUUID();
        message = UUID.randomUUID();
        coupon = UUID.randomUUID();
        couponTwo = UUID.randomUUID();
        couponThree = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO campaigns(id,name,description,landing_page_headline,landing_page_body,status,starts_at,ends_at,
                            audience_segment_id,audience_rules,published_at,created_by_user_id,created_at,updated_at)
                        VALUES(?,?,NULL,'Headline','Body','ACTIVE',?,?,NULL,'{}'::jsonb,?,?,?,?)
                        """, campaign, "Analytics test", Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now), ADMIN, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO campaign_audience_members(campaign_id,member_id,snapshot_at) VALUES(?,?,?)", campaign, MEMBER, Timestamp.from(now));
        jdbc.update("INSERT INTO campaign_messages(id,campaign_id,title,body,created_at,updated_at) VALUES(?,?, 'Title','Body',?,?)", message, campaign, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO member_messages(id,campaign_message_id,campaign_id,member_id,delivered_at,created_at) VALUES(?,?,?,?,?,?)", UUID.randomUUID(), message, campaign, MEMBER, Timestamp.from(now), Timestamp.from(now));
        insertCoupon(coupon, "Coupon A", 10, 1, now);
        insertCoupon(couponTwo, "Coupon B", 5, 1, now);
        insertCoupon(couponThree, "Coupon C", 3, 0, now);
        jdbc.update("INSERT INTO coupon_claims(id,campaign_id,campaign_coupon_id,member_id,idempotency_key,claimed_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), campaign, coupon, MEMBER, UUID.randomUUID(), Timestamp.from(now));
        jdbc.update("INSERT INTO coupon_claims(id,campaign_id,campaign_coupon_id,member_id,idempotency_key,claimed_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), campaign, couponTwo, MEMBER, UUID.randomUUID(), Timestamp.from(now));
        event(MEMBER, "IMPRESSION", message, null, "{}");
        event(MEMBER, "IMPRESSION", message, null, "{}");
        event(MEMBER, "CLICK", message, null, "{}");
        event(MEMBER, "LANDING_PAGE_VIEW", null, null, "{}");
        event(MEMBER, "CLAIM_ATTEMPT", null, coupon, "{\"outcome\":\"SUCCEEDED\"}");
        event(MEMBER, "CLAIM_ATTEMPT", null, couponTwo, "{\"outcome\":\"SUCCEEDED\"}");
        event(MEMBER, "CLAIM_ATTEMPT", null, coupon, "{\"outcome\":\"FAILED\"}");
        event(MEMBER, "CLAIM_ATTEMPT", null, coupon, "{\"outcome\":\"REPLAYED\"}");
        event(OUTSIDE_MEMBER, "LANDING_PAGE_VIEW", null, null, "{}");
    }

    private void insertCoupon(UUID id, String name, int total, int claimed, Instant now) {
        jdbc.update("""
                INSERT INTO campaign_coupons(id,campaign_id,name,description,discount_type,discount_value,minimum_order_amount,
                    total_quantity,claimed_quantity,starts_at,ends_at,created_at,updated_at)
                VALUES(?,?,?,NULL,'FIXED_AMOUNT',100,NULL,?,?,?,?,?,?)
                """, id, campaign, name, total, claimed, Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now), Timestamp.from(now));
    }

    private void event(UUID member, String type, UUID messageId, UUID couponId, String metadata) {
        jdbc.update("""
                INSERT INTO campaign_events(id,event_id,campaign_id,member_id,campaign_message_id,campaign_coupon_id,event_type,
                    occurred_at,received_at,metadata) VALUES(?,?,?,?,?,?,?,now(),now(),CAST(? AS jsonb))
                """, UUID.randomUUID(), UUID.randomUUID(), campaign, member, messageId, couponId, type, metadata);
    }

    @Test
    void computesCampaignAndCouponMetricsFromAuthoritativeTablesWithoutJoinFanOut() {
        var report = service.get(campaign);
        assertThat(report.targetMembers()).isEqualTo(1);
        assertThat(report.deliveredMembers()).isEqualTo(1);
        assertThat(report.deliveryRate().value()).isEqualByComparingTo("1.00000000");
        assertThat(report.impressionCount()).isEqualTo(2);
        assertThat(report.uniqueReach()).isEqualTo(1);
        assertThat(report.clickCount()).isEqualTo(1);
        assertThat(report.clickRate().value()).isEqualByComparingTo("1.00000000");
        assertThat(report.uniqueClickers()).isEqualTo(1);
        assertThat(report.landingPageViews()).isEqualTo(2);
        assertThat(report.uniqueLandingVisitors()).isEqualTo(1);
        assertThat(report.targetLandingVisitorRate().value()).isEqualByComparingTo("1.00000000");
        assertThat(report.couponClaims()).isEqualTo(2);
        assertThat(report.uniqueClaimers()).isEqualTo(1);
        assertThat(report.claimAttemptCount()).isEqualTo(4);
        assertThat(report.failedClaimAttemptCount()).isEqualTo(1);
        assertThat(report.claimAttemptsByOutcome().succeeded()).isEqualTo(2);
        assertThat(report.claimAttemptsByOutcome().failed()).isEqualTo(1);
        assertThat(report.claimAttemptsByOutcome().replayed()).isEqualTo(1);
        assertThat(report.coupons()).hasSize(3);
        var couponA = report.coupons().stream().filter(c -> c.name().equals("Coupon A")).findFirst().orElseThrow();
        var couponB = report.coupons().stream().filter(c -> c.name().equals("Coupon B")).findFirst().orElseThrow();
        var couponC = report.coupons().stream().filter(c -> c.name().equals("Coupon C")).findFirst().orElseThrow();
        assertThat(couponA.successfulClaims()).isEqualTo(1);
        assertThat(couponA.remainingQuantity()).isEqualTo(9);
        assertThat(couponA.uniqueClaimers()).isEqualTo(1);
        assertThat(couponB.successfulClaims()).isEqualTo(1);
        assertThat(couponB.uniqueClaimers()).isEqualTo(1);
        assertThat(couponC.targetClaimRate().value()).isEqualByComparingTo("0E-8");
    }

    @Test
    void zeroTargetCampaignRatesAreNullAndInventoryMismatchFailsClosed() {
        jdbc.update("DELETE FROM campaign_audience_members WHERE campaign_id=?", campaign);
        var report = service.get(campaign);
        assertThat(report.deliveryRate().value()).isNull();
        assertThat(report.reachRate().value()).isNull();
        assertThat(report.clickRate().value()).isEqualByComparingTo("1.00000000");
        assertThat(report.targetClaimRate().value()).isNull();
        jdbc.update("UPDATE campaign_coupons SET claimed_quantity=0 WHERE id=?", coupon);
        assertThatThrownBy(() -> service.get(campaign)).isInstanceOf(CampaignAnalyticsIntegrityException.class);
    }

    @Test
    void zeroActivityAndZeroTargetRatesAreUnavailable() {
        jdbc.update("DELETE FROM campaign_events WHERE campaign_id=?", campaign);
        jdbc.update("DELETE FROM coupon_claims WHERE campaign_id=?", campaign);
        jdbc.update("UPDATE campaign_coupons SET claimed_quantity=0 WHERE campaign_id=?", campaign);
        jdbc.update("DELETE FROM member_messages WHERE campaign_id=?", campaign);
        var noActivity = service.get(campaign);
        assertThat(noActivity.targetMembers()).isEqualTo(1);
        assertThat(noActivity.deliveredMembers()).isZero();
        assertThat(noActivity.impressionCount()).isZero();
        assertThat(noActivity.couponClaims()).isZero();
        assertThat(noActivity.clickRate().value()).isNull();
        assertThat(noActivity.coupons().stream().filter(c -> c.name().equals("Coupon A")).findFirst().orElseThrow().remainingQuantity()).isEqualTo(10);
        jdbc.update("DELETE FROM campaign_audience_members WHERE campaign_id=?", campaign);
        var noTarget = service.get(campaign);
        assertThat(noTarget.deliveryRate().value()).isNull();
        assertThat(noTarget.reachRate().value()).isNull();
        assertThat(noTarget.targetLandingVisitorRate().value()).isNull();
        assertThat(noTarget.targetClaimRate().value()).isNull();
    }

    @Test
    void adminCanReadButMemberAndAnonymousCannotReadAnalytics() throws Exception {
        String admin = tokens.createAccessToken(new UserPrincipal(ADMIN, "ADMIN"));
        String member = tokens.createAccessToken(new UserPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000002"), "MEMBER"));
        mvc.perform(get("/api/v1/admin/campaigns/{id}/analytics", campaign).cookie(new Cookie("access_token", admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.campaignId").value(campaign.toString()))
                .andExpect(jsonPath("$.data.coupons.length()").value(3))
                .andExpect(jsonPath("$.data.memberId").doesNotExist())
                .andExpect(jsonPath("$.data.rawEvents").doesNotExist());
        mvc.perform(get("/api/v1/admin/campaigns/{id}/analytics", campaign).cookie(new Cookie("access_token", member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/campaigns/{id}/analytics", campaign)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE campaign_coupons SET claimed_quantity=0 WHERE id=?", coupon);
        mvc.perform(get("/api/v1/admin/campaigns/{id}/analytics", campaign).cookie(new Cookie("access_token", admin)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ANALYTICS_INTEGRITY_ERROR"));
        mvc.perform(get("/api/v1/admin/campaigns/{id}/analytics", UUID.randomUUID()).cookie(new Cookie("access_token", admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownCampaignIsNotFound() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(CampaignAnalyticsService.CampaignAnalyticsNotFoundException.class);
    }
}
