package com.example.marketingplatform.database;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class DatabaseSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesAllTwelveApprovedTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "members", "refresh_token_sessions", "audience_segments",
                "campaigns", "campaign_audience_members", "campaign_coupons", "coupon_claims",
                "campaign_messages", "campaign_delivery_jobs", "member_messages", "campaign_events");

        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        assertThat(appliedMigrations).isEqualTo(2);
    }

    @Test
    void seedDataContainsOneAdminAndThreeFictionalMembers() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'MEMBER'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE '%@example.test'", Integer.class)).isEqualTo(4);
    }

    @Test
    void rejectsUnsupportedUserRole() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES ('30000000-0000-4000-8000-000000000001', 'invalid-role@example.test', 'hash', 'SUPERUSER', 'ACTIVE', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeMemberOrderCount() {
        insertUser("30000000-0000-4000-8000-000000000002", "negative-member@example.test", "MEMBER");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO members (id, user_id, display_name, registered_at, membership_tier, order_count,
                                     total_spending, created_at, updated_at)
                VALUES ('40000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002',
                        'Invalid Member', now(), 'STANDARD', -1, 0, now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCampaignWithoutAudienceDefinition() {
        insertUser("30000000-0000-4000-8000-000000000003", "campaign-owner@example.test", "ADMIN");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaigns (id, name, landing_page_headline, landing_page_body, status,
                                       starts_at, ends_at, created_by_user_id, created_at, updated_at)
                VALUES ('50000000-0000-4000-8000-000000000001', 'Invalid campaign', 'Headline', 'Body', 'DRAFT',
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-02-01 00:00:00+00',
                        '30000000-0000-4000-8000-000000000003', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidCampaignStatus() {
        insertUser("30000000-0000-4000-8000-000000000004", "campaign-owner-2@example.test", "ADMIN");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaigns (id, name, landing_page_headline, landing_page_body, status,
                                       starts_at, ends_at, audience_rules, created_by_user_id, created_at, updated_at)
                VALUES ('50000000-0000-4000-8000-000000000002', 'Invalid status', 'Headline', 'Body', 'ENDED',
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-02-01 00:00:00+00',
                        '{"version":1,"match":"ALL","conditions":[]}'::jsonb,
                        '30000000-0000-4000-8000-000000000004', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCouponWithZeroQuantity() {
        insertBaseFixture();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaign_coupons (id, campaign_id, name, discount_type, discount_value,
                                              total_quantity, claimed_quantity, starts_at, ends_at, created_at, updated_at)
                VALUES ('60000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000001',
                        'Zero quantity', 'FIXED_AMOUNT', 100, 0, 0,
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-02-01 00:00:00+00', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClaimThatPairsCampaignWithAnotherCampaignsCoupon() {
        insertBaseFixture();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coupon_claims (id, campaign_id, campaign_coupon_id, member_id, idempotency_key, claimed_at)
                VALUES ('70000000-0000-4000-8000-000000000001',
                        '50000000-0000-4000-8000-000000000001',
                        '60000000-0000-4000-8000-000000000002',
                        '21000000-0000-4000-8000-000000000001',
                        '70000000-0000-4000-8000-000000000011', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDuplicateSuccessfulClaimForCampaignCouponAndMember() {
        insertBaseFixture();
        insertClaim("70000000-0000-4000-8000-000000000002", "60000000-0000-4000-8000-000000000001",
                "70000000-0000-4000-8000-000000000012");

        assertThatThrownBy(() -> insertClaim("70000000-0000-4000-8000-000000000003",
                "60000000-0000-4000-8000-000000000001", "70000000-0000-4000-8000-000000000013"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesMemberScopedIdempotencyKeyUniqueness() {
        insertBaseFixture();
        insertClaim("70000000-0000-4000-8000-000000000020", "60000000-0000-4000-8000-000000000001",
                "70000000-0000-4000-8000-000000000020");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coupon_claims (id, campaign_id, campaign_coupon_id, member_id, idempotency_key, claimed_at)
                VALUES ('70000000-0000-4000-8000-000000000021',
                        '50000000-0000-4000-8000-000000000002',
                        '60000000-0000-4000-8000-000000000002',
                        '21000000-0000-4000-8000-000000000001',
                        '70000000-0000-4000-8000-000000000020', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRefreshTokenReplacementAcrossTokenFamilies() {
        insertUser("30000000-0000-4000-8000-000000000005", "token-user-one@example.test", "MEMBER");
        insertUser("30000000-0000-4000-8000-000000000006", "token-user-two@example.test", "MEMBER");
        jdbc.update("""
                INSERT INTO refresh_token_sessions (id, token_family_id, user_id, token_hash, expires_at, created_at)
                VALUES ('80000000-0000-4000-8000-000000000001', '81000000-0000-4000-8000-000000000001',
                        '30000000-0000-4000-8000-000000000005', 'hash-one', now() + interval '7 days', now()),
                       ('80000000-0000-4000-8000-000000000002', '81000000-0000-4000-8000-000000000002',
                        '30000000-0000-4000-8000-000000000006', 'hash-two', now() + interval '7 days', now())
                """);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE refresh_token_sessions
                SET replaced_by_token_id = '80000000-0000-4000-8000-000000000002'
                WHERE id = '80000000-0000-4000-8000-000000000001'
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDeliveryCursorOutsideItsCampaignSnapshot() {
        insertBaseFixture();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaign_delivery_jobs (id, campaign_id, status, cursor_member_id, created_at, updated_at)
                VALUES ('90000000-0000-4000-8000-000000000002',
                        '50000000-0000-4000-8000-000000000002', 'PENDING',
                        '21000000-0000-4000-8000-000000000001', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsImpressionWithoutMatchingMemberInboxMessage() {
        insertBaseFixture();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaign_events (id, event_id, campaign_id, member_id, campaign_message_id,
                                             event_type, occurred_at, received_at)
                VALUES ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001',
                        '50000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000001',
                        '52000000-0000-4000-8000-000000000002', 'IMPRESSION', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClaimAttemptWithoutAllowedOutcome() {
        insertBaseFixture();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaign_events (id, event_id, campaign_id, member_id, event_type,
                                             occurred_at, received_at, metadata)
                VALUES ('a0000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000002',
                        '50000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001',
                        'CLAIM_ATTEMPT', now(), now(), '{"outcome":"UNKNOWN"}'::jsonb)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertBaseFixture() {
        insertUser("30000000-0000-4000-8000-000000000010", "fixture-admin@example.test", "ADMIN");
        insertUser("30000000-0000-4000-8000-000000000011", "fixture-member@example.test", "MEMBER");
        jdbc.update("""
                INSERT INTO members (id, user_id, display_name, registered_at, membership_tier, created_at, updated_at)
                VALUES ('21000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000011',
                        'Fixture Member', now(), 'STANDARD', now(), now())
                """);
        jdbc.update("""
                INSERT INTO audience_segments (id, name, rules, created_by_user_id, created_at, updated_at)
                VALUES ('51000000-0000-4000-8000-000000000001', 'Fixture segment',
                        '{"version":1,"match":"ALL","conditions":[]}'::jsonb,
                        '30000000-0000-4000-8000-000000000010', now(), now())
                """);
        jdbc.update("""
                INSERT INTO campaigns (id, name, landing_page_headline, landing_page_body, status,
                                       starts_at, ends_at, audience_segment_id, created_by_user_id, created_at, updated_at)
                VALUES ('50000000-0000-4000-8000-000000000001', 'Fixture campaign one', 'Headline', 'Body', 'ACTIVE',
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2027-01-01 00:00:00+00',
                        '51000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000010', now(), now()),
                       ('50000000-0000-4000-8000-000000000002', 'Fixture campaign two', 'Headline', 'Body', 'ACTIVE',
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2027-01-01 00:00:00+00',
                        '51000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000010', now(), now())
                """);
        jdbc.update("""
                INSERT INTO campaign_audience_members (campaign_id, member_id, snapshot_at)
                VALUES ('50000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', now())
                """);
        jdbc.update("""
                INSERT INTO campaign_coupons (id, campaign_id, name, discount_type, discount_value,
                                              total_quantity, claimed_quantity, starts_at, ends_at, created_at, updated_at)
                VALUES ('60000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
                        'Fixture coupon one', 'FIXED_AMOUNT', 100, 10, 0,
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2027-01-01 00:00:00+00', now(), now()),
                       ('60000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000002',
                        'Fixture coupon two', 'PERCENTAGE', 10, 10, 0,
                        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2027-01-01 00:00:00+00', now(), now())
                """);
        jdbc.update("""
                INSERT INTO campaign_messages (id, campaign_id, title, body, created_at, updated_at)
                VALUES ('52000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 'Message one', 'Body', now(), now()),
                       ('52000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000002', 'Message two', 'Body', now(), now())
                """);
        jdbc.update("""
                INSERT INTO campaign_delivery_jobs (id, campaign_id, status, cursor_member_id, created_at, updated_at)
                VALUES ('90000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
                        'PENDING', '21000000-0000-4000-8000-000000000001', now(), now())
                """);
        jdbc.update("""
                INSERT INTO member_messages (id, campaign_message_id, campaign_id, member_id, delivered_at, created_at)
                VALUES ('53000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000001',
                        '50000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', now(), now())
                """);
    }

    private void insertUser(String id, String email, String role) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?::uuid, ?, 'test-hash', ?, 'ACTIVE', now(), now())
                """, id, email, role);
    }

    private void insertClaim(String claimId, String couponId, String idempotencyKey) {
        jdbc.update("""
                INSERT INTO coupon_claims (id, campaign_id, campaign_coupon_id, member_id, idempotency_key, claimed_at)
                VALUES (?::uuid, '50000000-0000-4000-8000-000000000001', ?::uuid,
                        '21000000-0000-4000-8000-000000000001', ?::uuid, now())
                """, claimId, couponId, idempotencyKey);
    }
}
