package com.example.marketingplatform.engagement;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.coupon.application.CouponService;
import com.example.marketingplatform.engagement.application.CampaignExperienceService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CampaignExperienceIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID MEMBER_USER = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_MEMBER_USER = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID ADMIN_USER = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CampaignExperienceService service;
    @Autowired
    CouponService couponService;

    private UUID campaignId;
    private UUID couponId;
    private UUID campaignMessageId;
    private UUID inboxMessageId;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        couponId = UUID.randomUUID();
        campaignMessageId = UUID.randomUUID();
        inboxMessageId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO campaigns(id,name,description,landing_page_headline,landing_page_body,status,starts_at,ends_at,
                            audience_segment_id,audience_rules,published_at,created_by_user_id,created_at,updated_at)
                        VALUES(?,?,?,?,?,'ACTIVE',?,?,NULL,'{}'::jsonb,?,?,?,?)
                        """, campaignId, "Phase 8 test", "Test description", "Test headline", "Test body",
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(86400)), Timestamp.from(now), ADMIN_USER,
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO campaign_audience_members(campaign_id,member_id,snapshot_at) VALUES(?,?,?)",
                campaignId, MEMBER_ID, Timestamp.from(now));
        jdbc.update("""
                        INSERT INTO campaign_coupons(id,campaign_id,name,description,discount_type,discount_value,minimum_order_amount,
                            total_quantity,claimed_quantity,starts_at,ends_at,created_at,updated_at)
                        VALUES(?,?,?,'Test coupon','FIXED_AMOUNT',100,NULL,10,0,?,?,?,?)
                        """, couponId, campaignId, "Test coupon", Timestamp.from(now.minusSeconds(3600)),
                Timestamp.from(now.plusSeconds(86400)), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO campaign_messages(id,campaign_id,title,body,created_at,updated_at) VALUES(?,?,?, ?,?,?)",
                campaignMessageId, campaignId, "Test message", "Test message body", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO member_messages(id,campaign_message_id,campaign_id,member_id,delivered_at,created_at) VALUES(?,?,?,?,?,?)",
                inboxMessageId, campaignMessageId, campaignId, MEMBER_ID, Timestamp.from(now), Timestamp.from(now));
    }

    @Test
    void publicLandingAndCouponReadsOmitMemberStateForAnonymousVisitors() {
        var landing = service.landing(campaignId, null);
        var coupons = service.coupons(campaignId, null, 0, 20);
        assertThat(landing.landingPage().headline()).isEqualTo("Test headline");
        assertThat(landing.memberState()).isNull();
        assertThat(coupons.memberState()).isNull();
        assertThat(coupons.data()).singleElement().satisfies(coupon -> assertThat(coupon.memberStatus()).isEqualTo("AVAILABLE"));
        Integer claimedQuantity = jdbc.queryForObject("SELECT claimed_quantity FROM campaign_coupons WHERE id=?", Integer.class, couponId);
        assertThat(claimedQuantity).isEqualTo(0);
    }

    @Test
    void memberCanClaimCouponAndIdempotentReplayDoesNotReserveInventoryTwice() {
        UUID idempotencyKey = UUID.randomUUID();

        var first = couponService.claim(campaignId, couponId, MEMBER_USER, idempotencyKey);
        var replay = couponService.claim(campaignId, couponId, MEMBER_USER, idempotencyKey);

        assertThat(first.status()).isEqualTo("CLAIMED");
        assertThat(replay.claimId()).isEqualTo(first.claimId());
        assertThat(jdbc.queryForObject("SELECT claimed_quantity FROM campaign_coupons WHERE id=?", Integer.class, couponId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_claims WHERE campaign_id=? AND campaign_coupon_id=? AND member_id=?", Integer.class, campaignId, couponId, MEMBER_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_events WHERE campaign_id=? AND member_id=? AND event_type='CLAIM_ATTEMPT'", Integer.class, campaignId, MEMBER_ID)).isEqualTo(2);
    }

    @Test
    void alreadyClaimedMemberSeesClaimedState() {
        Instant now = Instant.now();
        jdbc.update("UPDATE campaign_coupons SET claimed_quantity=1 WHERE id=?", couponId);
        jdbc.update("INSERT INTO coupon_claims(id,campaign_id,campaign_coupon_id,member_id,idempotency_key,claimed_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), campaignId, couponId, MEMBER_ID, UUID.randomUUID(), Timestamp.from(now));
        var coupons = service.coupons(campaignId, new UserPrincipal(MEMBER_USER, "MEMBER"), 0, 20);
        assertThat(coupons.data().getFirst().memberStatus()).isEqualTo("ALREADY_CLAIMED");
    }

    @Test
    void soldOutCouponIsUnavailable() {
        jdbc.update("UPDATE campaign_coupons SET claimed_quantity=total_quantity WHERE id=?", couponId);
        var coupon = service.coupons(campaignId, null, 0, 20).data().getFirst();
        assertThat(coupon.memberStatus()).isEqualTo("UNAVAILABLE");
        assertThat(coupon.unavailableReason()).isEqualTo("COUPON_SOLD_OUT");
    }

    @Test
    void memberEligibilityComesFromTheImmutableAudienceSnapshot() {
        var eligible = service.coupons(campaignId, new UserPrincipal(MEMBER_USER, "MEMBER"), 0, 20);
        var notEligible = service.coupons(campaignId, new UserPrincipal(OTHER_MEMBER_USER, "MEMBER"), 0, 20);
        assertThat(eligible.memberState().eligible()).isTrue();
        assertThat(eligible.data().getFirst().memberStatus()).isEqualTo("AVAILABLE");
        assertThat(notEligible.memberState().eligible()).isFalse();
        assertThat(notEligible.data().getFirst().memberStatus()).isEqualTo("NOT_ELIGIBLE");
    }

    @Test
    void engagementUsesInboxOwnershipAndDeduplicatesByMemberAndEventId() {
        UUID eventId = UUID.randomUUID();
        var member = new UserPrincipal(MEMBER_USER, "MEMBER");
        var event = new CampaignExperienceService.EventWrite(eventId, "IMPRESSION", inboxMessageId, Instant.now().toString());
        service.recordEvent(campaignId, member, event);
        service.recordEvent(campaignId, member, event);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM campaign_events WHERE member_id=? AND event_id=?", Integer.class, MEMBER_ID, eventId);
        UUID storedMessage = jdbc.queryForObject("SELECT campaign_message_id FROM campaign_events WHERE member_id=? AND event_id=?", UUID.class, MEMBER_ID, eventId);
        assertThat(count).isEqualTo(1);
        assertThat(storedMessage).isEqualTo(campaignMessageId);
    }

    @Test
    void landingPageViewIsAcceptedWithoutMessageContextAndDeduplicated() {
        UUID eventId = UUID.randomUUID();
        var member = new UserPrincipal(MEMBER_USER, "MEMBER");
        var event = new CampaignExperienceService.EventWrite(eventId, "LANDING_PAGE_VIEW", null, Instant.now().toString());
        service.recordEvent(campaignId, member, event);
        service.recordEvent(campaignId, member, event);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM campaign_events WHERE member_id=? AND event_id=?", Integer.class, MEMBER_ID, eventId);
        assertThat(count).isEqualTo(1);
        assertThatThrownBy(() -> service.recordEvent(campaignId, member,
                new CampaignExperienceService.EventWrite(UUID.randomUUID(), "LANDING_PAGE_VIEW", inboxMessageId, Instant.now().toString())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventRejectsAnInboxMessageOwnedByAnotherMemberAndAdminWrites() {
        UUID eventId = UUID.randomUUID();
        var otherMember = new UserPrincipal(OTHER_MEMBER_USER, "MEMBER");
        var event = new CampaignExperienceService.EventWrite(eventId, "CLICK", inboxMessageId, Instant.now().toString());
        assertThatThrownBy(() -> service.recordEvent(campaignId, otherMember, event)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordEvent(campaignId, new UserPrincipal(ADMIN_USER, "ADMIN"),
                new CampaignExperienceService.EventWrite(UUID.randomUUID(), "LANDING_PAGE_VIEW", null, Instant.now().toString())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unpublishedCampaignIsNotPubliclyReadable() {
        jdbc.update("UPDATE campaigns SET published_at=NULL WHERE id=?", campaignId);
        assertThatThrownBy(() -> service.landing(campaignId, null)).isInstanceOf(CampaignExperienceService.ResourceNotFound.class);
        assertThatThrownBy(() -> service.coupons(campaignId, null, 0, 20)).isInstanceOf(CampaignExperienceService.ResourceNotFound.class);
    }

    @Test
    void pausedAndEndedPublishedCampaignsRemainReadableButCouponsAreUnavailable() {
        jdbc.update("UPDATE campaigns SET status='PAUSED' WHERE id=?", campaignId);
        var paused = service.landing(campaignId, null);
        var coupon = service.coupons(campaignId, null, 0, 20).data().getFirst();
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.claimable()).isFalse();
        assertThat(coupon.memberStatus()).isEqualTo("UNAVAILABLE");
        jdbc.update("UPDATE campaigns SET ends_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(1)), campaignId);
        assertThat(service.landing(campaignId, null).status()).isEqualTo("ENDED");
    }
}
