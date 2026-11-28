package com.example.marketingplatform.coupon;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.coupon.application.CouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID ADMIN_USER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final int DISTINCT_MEMBERS = 24;
    private static final int SAME_MEMBER_REQUESTS = 12;
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> memberIds = new ArrayList<>();
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CouponService couponService;
    private UUID campaignId;
    private UUID firstCouponId;
    private UUID secondCouponId;

    @BeforeEach
    void createIsolatedFixture() {
        userIds.clear();
        memberIds.clear();
        Instant now = Instant.now();
        campaignId = UUID.randomUUID();
        firstCouponId = UUID.randomUUID();
        secondCouponId = UUID.randomUUID();

        jdbc.update("""
                        INSERT INTO campaigns(id,name,description,landing_page_headline,landing_page_body,status,
                            starts_at,ends_at,audience_segment_id,audience_rules,published_at,created_by_user_id,created_at,updated_at)
                        VALUES(?,?,NULL,'Concurrency test','Fixture','ACTIVE',?,?,NULL,'{}'::jsonb,?,?,?,?)
                        """, campaignId, "Phase 10 concurrency " + campaignId,
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now), ADMIN_USER_ID, Timestamp.from(now), Timestamp.from(now));

        insertCoupon(firstCouponId, "Limited inventory", 8, now);
        insertCoupon(secondCouponId, "Per-member uniqueness", 20, now);

        for (int i = 0; i < DISTINCT_MEMBERS; i++) {
            UUID userId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            userIds.add(userId);
            memberIds.add(memberId);
            jdbc.update("""
                            INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at)
                            VALUES(?,?,?,'MEMBER','ACTIVE',?,?)
                            """, userId, "phase10-" + userId + "@example.test",
                    "$2a$10$Bs/3dZfIpkNxhM2YAQkaweV5HymyS3ofo0rebYq1KVdxyFTlr1UjW",
                    Timestamp.from(now), Timestamp.from(now));
            jdbc.update("""
                            INSERT INTO members(id,user_id,display_name,registered_at,membership_tier,
                                order_count,total_spending,created_at,updated_at)
                            VALUES(?,?,?,?,'STANDARD',0,0,?,?)
                            """, memberId, userId, "Phase 10 Member " + i,
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO campaign_audience_members(campaign_id,member_id,snapshot_at)
                    VALUES(?,?,?)
                    """, campaignId, memberId, Timestamp.from(now));
        }
    }

    @AfterEach
    void removeFixture() {
        if (campaignId != null) {
            jdbc.update("DELETE FROM campaign_events WHERE campaign_id=?", campaignId);
            jdbc.update("DELETE FROM coupon_claims WHERE campaign_id=?", campaignId);
            jdbc.update("DELETE FROM campaign_audience_members WHERE campaign_id=?", campaignId);
            jdbc.update("DELETE FROM campaign_coupons WHERE campaign_id=?", campaignId);
            jdbc.update("DELETE FROM campaigns WHERE id=?", campaignId);
        }
        for (UUID memberId : memberIds) {
            jdbc.update("DELETE FROM members WHERE id=?", memberId);
        }
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }

    @RepeatedTest(3)
    void concurrentDifferentMembersNeverOversellAndCountersMatchClaims() throws Exception {
        int quantity = 8;
        int requestCount = DISTINCT_MEMBERS;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                final int memberIndex = i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start.");
                    }
                    try {
                        couponService.claim(campaignId, firstCouponId, userIds.get(memberIndex), UUID.randomUUID());
                        return true;
                    } catch (CouponService.Conflict conflict) {
                        assertThat(conflict.code).isEqualTo("COUPON_SOLD_OUT");
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(quantity);
            assertCouponState(firstCouponId, quantity, quantity);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM coupon_claims
                    WHERE campaign_id=? AND campaign_coupon_id=?
                    """, Integer.class, campaignId, firstCouponId)).isEqualTo(quantity);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @RepeatedTest(3)
    void concurrentSameMemberSameIdempotencyKeyProducesOneLogicalClaim() throws Exception {
        UUID sameUser = userIds.getFirst();
        UUID sameKey = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(SAME_MEMBER_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(SAME_MEMBER_REQUESTS);
        try {
            List<Future<UUID>> results = new ArrayList<>();
            for (int i = 0; i < SAME_MEMBER_REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start.");
                    }
                    return couponService.claim(campaignId, secondCouponId, sameUser, sameKey).claimId();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UUID> claimIds = new ArrayList<>();
            for (Future<UUID> result : results) {
                claimIds.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(claimIds).containsOnly(claimIds.getFirst());
            assertCouponState(secondCouponId, 20, 1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM coupon_claims
                    WHERE campaign_id=? AND campaign_coupon_id=? AND member_id=?
                    """, Integer.class, campaignId, secondCouponId, memberIds.getFirst())).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @RepeatedTest(3)
    void concurrentSameMemberDifferentKeysStillAllowsOnlyOneClaim() throws Exception {
        UUID sameUser = userIds.get(1);
        CountDownLatch ready = new CountDownLatch(SAME_MEMBER_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(SAME_MEMBER_REQUESTS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < SAME_MEMBER_REQUESTS; i++) {
                UUID key = UUID.randomUUID();
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start.");
                    }
                    try {
                        couponService.claim(campaignId, secondCouponId, sameUser, key);
                        succeeded.incrementAndGet();
                    } catch (CouponService.Conflict conflict) {
                        assertThat(conflict.code).isEqualTo("CLAIM_ALREADY_EXISTS");
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }

            assertThat(succeeded.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(SAME_MEMBER_REQUESTS - 1);
            assertCouponState(secondCouponId, 20, 1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM coupon_claims
                    WHERE campaign_id=? AND campaign_coupon_id=? AND member_id=?
                    """, Integer.class, campaignId, secondCouponId, memberIds.get(1))).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void insertCoupon(UUID couponId, String name, int quantity, Instant now) {
        jdbc.update("""
                        INSERT INTO campaign_coupons(id,campaign_id,name,description,discount_type,discount_value,
                            minimum_order_amount,total_quantity,claimed_quantity,starts_at,ends_at,created_at,updated_at)
                        VALUES(?,?,?,NULL,'FIXED_AMOUNT',100,NULL,?,0,?,?,?,?)
                        """, couponId, campaignId, name, quantity,
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now), Timestamp.from(now));
    }

    private void assertCouponState(UUID couponId, int total, int expectedClaimed) {
        Integer claimed = jdbc.queryForObject(
                "SELECT claimed_quantity FROM campaign_coupons WHERE id=?", Integer.class, couponId);
        Integer successfulRows = jdbc.queryForObject(
                "SELECT count(*) FROM coupon_claims WHERE campaign_id=? AND campaign_coupon_id=?",
                Integer.class, campaignId, couponId);

        assertThat(claimed).isEqualTo(expectedClaimed);
        assertThat(successfulRows).isEqualTo(expectedClaimed);
        assertThat(claimed).isBetween(0, total);
        assertThat(total - claimed).isGreaterThanOrEqualTo(0);
    }
}
