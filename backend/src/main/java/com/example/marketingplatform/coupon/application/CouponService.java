package com.example.marketingplatform.coupon.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CouponService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final JsonMapper mapper;

    public CouponService(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx, Clock clock, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.mapper = mapper;
    }

    public Coupon create(UUID campaignId, CouponWrite input) {
        validate(input);
        return Objects.requireNonNull(tx.execute(s -> {
            ensureDraft(campaignId);
            Instant now = clock.instant();
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO campaign_coupons(id,campaign_id,name,description,discount_type,discount_value,minimum_order_amount,total_quantity,claimed_quantity,starts_at,ends_at,created_at,updated_at)
                    VALUES(:id,:campaign,:name,:description,:type,:value,:minimum,:quantity,0,:starts,:ends,:now,:now)""", params(id, campaignId, input, now));
            return findCoupon(campaignId, id).orElseThrow();
        }));
    }

    @Transactional(readOnly = true)
    public List<Coupon> list(UUID campaignId) {
        if (!existsCampaign(campaignId)) throw new NotFound();
        return jdbc.query("SELECT * FROM campaign_coupons WHERE campaign_id=:campaign ORDER BY created_at ASC", Map.of("campaign", campaignId), this::mapCoupon);
    }

    public Coupon update(UUID campaignId, UUID couponId, CouponWrite input) {
        validate(input);
        return Objects.requireNonNull(tx.execute(s -> {
            ensureDraft(campaignId);
            int n = jdbc.update("""
                    UPDATE campaign_coupons SET name=:name,description=:description,discount_type=:type,discount_value=:value,
                    minimum_order_amount=:minimum,total_quantity=:quantity,starts_at=:starts,ends_at=:ends,updated_at=:now
                    WHERE id=:id AND campaign_id=:campaign AND claimed_quantity=0""", params(couponId, campaignId, input, clock.instant()));
            if (n == 0) {
                if (findCoupon(campaignId, couponId).isEmpty()) throw new NotFound();
                throw new Conflict("A coupon with claims cannot be reconfigured.", "CAMPAIGN_CONFLICT");
            }
            return findCoupon(campaignId, couponId).orElseThrow();
        }));
    }

    public Claim claim(UUID campaignId, UUID couponId, UUID userId, UUID key) {
        if (key == null) throw new IllegalArgumentException("idempotencyKey is required and must be a UUID.");
        UUID memberId = jdbc.query("SELECT id FROM members WHERE user_id=:user", Map.of("user", userId), (r, n) -> r.getObject(1, UUID.class)).stream().findFirst().orElseThrow(NotFound::new);
        try {
            return Objects.requireNonNull(tx.execute(status -> claimInTransaction(campaignId, couponId, memberId, key)));
        } catch (ClaimFailure failure) {
            writeAudit(campaignId, couponId, memberId, "FAILED", failure.code);
            if (failure.notFound) throw new NotFound();
            throw new Conflict(failure.getMessage(), failure.code);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            Claim existing = findByMemberKey(memberId, key).orElse(null);
            if (existing != null) {
                if (existing.campaignId().equals(campaignId) && existing.couponId().equals(couponId)) {
                    writeAudit(campaignId, couponId, memberId, "REPLAYED", null);
                    return existing;
                }
                writeAudit(campaignId, couponId, memberId, "FAILED", "IDEMPOTENCY_KEY_CONFLICT");
                throw new Conflict("Idempotency key was already used for another claim.", "IDEMPOTENCY_KEY_CONFLICT");
            }
            if (findByMemberCoupon(campaignId, couponId, memberId).isPresent()) {
                writeAudit(campaignId, couponId, memberId, "FAILED", "CLAIM_ALREADY_EXISTS");
                throw new Conflict("This coupon has already been claimed.", "CLAIM_ALREADY_EXISTS");
            }
            throw duplicate;
        }
    }

    private Claim claimInTransaction(UUID campaignId, UUID couponId, UUID memberId, UUID key) {
        // Serialize against Pause and keep lock until the transaction commits.
        List<String> campaign = jdbc.query("SELECT status FROM campaigns WHERE id=:id FOR SHARE", Map.of("id", campaignId), (r, n) -> r.getString(1));
        if (campaign.isEmpty()) throw new NotFound();
        Optional<Claim> prior = findByMemberKey(memberId, key);
        if (prior.isPresent()) {
            Claim c = prior.get();
            if (!c.campaignId().equals(campaignId) || !c.couponId().equals(couponId))
                throw new ClaimFailure("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key was already used for another claim.");
            writeAuditInTx(campaignId, couponId, memberId, "REPLAYED", null);
            return c;
        }
        Instant now = clock.instant();
        var camp = jdbc.query("SELECT status,published_at,starts_at,ends_at FROM campaigns WHERE id=:id", Map.of("id", campaignId), (r, n) -> new Object[]{r.getString(1), r.getTimestamp(2), r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant()}).get(0);
        if (!"ACTIVE".equals(camp[0]) || camp[1] == null || now.isBefore((Instant) camp[2]) || !now.isBefore((Instant) camp[3]))
            throw new ClaimFailure("CAMPAIGN_INACTIVE", "Campaign is not currently claimable.");
        var coupons = jdbc.query("SELECT starts_at,ends_at,claimed_quantity,total_quantity FROM campaign_coupons WHERE id=:coupon AND campaign_id=:campaign", Map.of("coupon", couponId, "campaign", campaignId), (r, n) -> new Object[]{r.getTimestamp(1).toInstant(), r.getTimestamp(2).toInstant(), r.getInt(3), r.getInt(4)});
        if (coupons.isEmpty())
            throw new ClaimFailure("RESOURCE_NOT_FOUND", "Coupon does not exist for this campaign.", true);
        Object[] c = coupons.get(0);
        if (now.isBefore((Instant) c[0]) || !now.isBefore((Instant) c[1]))
            throw new ClaimFailure("COUPON_OUTSIDE_VALIDITY", "Coupon is outside its validity window.");
        Integer eligible = jdbc.query("SELECT 1 FROM campaign_audience_members WHERE campaign_id=:campaign AND member_id=:member", Map.of("campaign", campaignId, "member", memberId), (r, n) -> 1).stream().findFirst().orElse(null);
        if (eligible == null)
            throw new ClaimFailure("MEMBER_NOT_ELIGIBLE", "Member is not in the campaign audience snapshot.");
        if (findByMemberCoupon(campaignId, couponId, memberId).isPresent())
            throw new ClaimFailure("CLAIM_ALREADY_EXISTS", "This coupon has already been claimed.");
        int reserved = jdbc.update("UPDATE campaign_coupons SET claimed_quantity=claimed_quantity+1,updated_at=:now WHERE id=:coupon AND campaign_id=:campaign AND claimed_quantity<total_quantity AND starts_at<=:now AND ends_at>:now", Map.of("now", Timestamp.from(now), "coupon", couponId, "campaign", campaignId));
        if (reserved != 1) throw new ClaimFailure("COUPON_SOLD_OUT", "No claimable inventory remains.");
        UUID claimId = UUID.randomUUID();
        jdbc.update("INSERT INTO coupon_claims(id,campaign_id,campaign_coupon_id,member_id,idempotency_key,claimed_at) VALUES(:id,:campaign,:coupon,:member,:key,:now)", Map.of("id", claimId, "campaign", campaignId, "coupon", couponId, "member", memberId, "key", key, "now", now));
        writeAuditInTx(campaignId, couponId, memberId, "SUCCEEDED", null);
        return new Claim(claimId, campaignId, couponId, "CLAIMED", now);
    }

    @Transactional(readOnly = true)
    public List<MemberClaim> memberClaims(UUID userId) {
        UUID member = jdbc.query("SELECT id FROM members WHERE user_id=:user", Map.of("user", userId), (r, n) -> r.getObject(1, UUID.class)).stream().findFirst().orElseThrow(NotFound::new);
        return jdbc.query("""
                SELECT cc.id claim_id,cc.campaign_id,cc.campaign_coupon_id,cc.claimed_at,c.name campaign_name,cp.name coupon_name,
                cp.description,cp.discount_type,cp.discount_value,cp.minimum_order_amount,cp.starts_at,cp.ends_at
                FROM coupon_claims cc JOIN campaigns c ON c.id=cc.campaign_id JOIN campaign_coupons cp ON cp.id=cc.campaign_coupon_id
                WHERE cc.member_id=:member ORDER BY cc.claimed_at DESC""", Map.of("member", member), (r, n) -> new MemberClaim(r.getObject("claim_id", UUID.class), r.getObject("campaign_id", UUID.class), r.getObject("campaign_coupon_id", UUID.class), r.getString("campaign_name"), r.getString("coupon_name"), r.getString("description"), r.getString("discount_type"), r.getBigDecimal("discount_value"), r.getBigDecimal("minimum_order_amount"), r.getTimestamp("starts_at").toInstant(), r.getTimestamp("ends_at").toInstant(), r.getTimestamp("claimed_at").toInstant()));
    }

    private void writeAudit(UUID campaign, UUID coupon, UUID member, String outcome, String code) {
        tx.executeWithoutResult(s -> writeAuditInTx(campaign, coupon, member, outcome, code));
    }

    private void writeAuditInTx(UUID campaign, UUID coupon, UUID member, String outcome, String code) {
        boolean campaignExists = existsCampaign(campaign);
        if (!campaignExists) return;
        UUID validCoupon = jdbc.query("SELECT id FROM campaign_coupons WHERE campaign_id=:campaign AND id=:coupon", Map.of("campaign", campaign, "coupon", coupon), (r, n) -> r.getObject(1, UUID.class)).stream().findFirst().orElse(null);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("outcome", outcome);
        if (code != null) meta.put("failureCode", code);
        if (validCoupon == null && coupon != null) meta.put("couponId", coupon.toString());
        try {
            jdbc.update("INSERT INTO campaign_events(id,event_id,campaign_id,member_id,campaign_coupon_id,event_type,occurred_at,received_at,metadata) VALUES(:id,:event,:campaign,:member,:coupon,'CLAIM_ATTEMPT',:now,:now,CAST(:metadata AS jsonb))", new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("event", UUID.randomUUID()).addValue("campaign", campaign).addValue("member", member).addValue("coupon", validCoupon).addValue("now", Timestamp.from(clock.instant())).addValue("metadata", mapper.writeValueAsString(meta)));
        } catch (Exception e) {
            throw new IllegalStateException("Required claim audit could not be persisted.", e);
        }
    }

    private Optional<Claim> findByMemberKey(UUID member, UUID key) {
        return jdbc.query("SELECT id,campaign_id,campaign_coupon_id,claimed_at FROM coupon_claims WHERE member_id=:member AND idempotency_key=:key", Map.of("member", member, "key", key), this::mapClaim).stream().findFirst();
    }

    private Optional<Claim> findByMemberCoupon(UUID campaign, UUID coupon, UUID member) {
        return jdbc.query("SELECT id,campaign_id,campaign_coupon_id,claimed_at FROM coupon_claims WHERE campaign_id=:campaign AND campaign_coupon_id=:coupon AND member_id=:member", Map.of("campaign", campaign, "coupon", coupon, "member", member), this::mapClaim).stream().findFirst();
    }

    private Optional<Coupon> findCoupon(UUID campaign, UUID id) {
        return jdbc.query("SELECT * FROM campaign_coupons WHERE campaign_id=:campaign AND id=:id", Map.of("campaign", campaign, "id", id), this::mapCoupon).stream().findFirst();
    }

    private boolean existsCampaign(UUID id) {
        return !jdbc.query("SELECT 1 FROM campaigns WHERE id=:id", Map.of("id", id), (r, n) -> 1).isEmpty();
    }

    private void ensureDraft(UUID id) {
        String status = jdbc.query("SELECT status FROM campaigns WHERE id=:id FOR UPDATE", Map.of("id", id), (r, n) -> r.getString(1)).stream().findFirst().orElseThrow(NotFound::new);
        if (!"DRAFT".equals(status))
            throw new Conflict("Coupons can only be configured for DRAFT campaigns.", "CAMPAIGN_CONFLICT");
    }

    private MapSqlParameterSource params(UUID id, UUID campaign, CouponWrite i, Instant now) {
        return new MapSqlParameterSource().addValue("id", id).addValue("campaign", campaign).addValue("name", i.name().trim()).addValue("description", i.description()).addValue("type", i.discountType()).addValue("value", i.discountValue()).addValue("minimum", i.minimumOrderAmount()).addValue("quantity", i.totalQuantity()).addValue("starts", Timestamp.from(OffsetDateTime.parse(i.startsAt()).toInstant())).addValue("ends", Timestamp.from(OffsetDateTime.parse(i.endsAt()).toInstant())).addValue("now", Timestamp.from(now));
    }

    private void validate(CouponWrite i) {
        if (i == null || i.name() == null || i.name().isBlank() || i.name().length() > 160)
            throw new IllegalArgumentException("name is required and must be at most 160 characters.");
        if (!List.of("FIXED_AMOUNT", "PERCENTAGE").contains(i.discountType()))
            throw new IllegalArgumentException("discountType must be FIXED_AMOUNT or PERCENTAGE.");
        if (i.discountValue() == null || i.discountValue().compareTo(BigDecimal.ZERO) <= 0 || ("PERCENTAGE".equals(i.discountType()) && i.discountValue().compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("discountValue is invalid for the selected discountType.");
        if (i.minimumOrderAmount() != null && i.minimumOrderAmount().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("minimumOrderAmount must be non-negative.");
        if (i.totalQuantity() == null || i.totalQuantity() < 1)
            throw new IllegalArgumentException("totalQuantity must be at least 1.");
        try {
            if (!OffsetDateTime.parse(i.endsAt()).toInstant().isAfter(OffsetDateTime.parse(i.startsAt()).toInstant()))
                throw new IllegalArgumentException("endsAt must be after startsAt.");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("startsAt and endsAt must be ISO-8601 date-times with offsets.");
        }
    }

    private Coupon mapCoupon(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Coupon(r.getObject("id", UUID.class), r.getObject("campaign_id", UUID.class), r.getString("name"), r.getString("description"), r.getString("discount_type"), r.getBigDecimal("discount_value"), r.getBigDecimal("minimum_order_amount"), r.getInt("total_quantity"), r.getInt("claimed_quantity"), r.getTimestamp("starts_at").toInstant(), r.getTimestamp("ends_at").toInstant(), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }

    private Claim mapClaim(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Claim(r.getObject("id", UUID.class), r.getObject("campaign_id", UUID.class), r.getObject("campaign_coupon_id", UUID.class), "CLAIMED", r.getTimestamp("claimed_at").toInstant());
    }

    public record CouponWrite(String name, String description, String discountType, BigDecimal discountValue,
                              BigDecimal minimumOrderAmount, Integer totalQuantity, String startsAt, String endsAt) {
    }

    public record Coupon(UUID id, UUID campaignId, String name, String description, String discountType,
                         BigDecimal discountValue, BigDecimal minimumOrderAmount, int totalQuantity,
                         int claimedQuantity, Instant startsAt, Instant endsAt, Instant createdAt, Instant updatedAt) {
    }

    public record Claim(UUID claimId, UUID campaignId, UUID couponId, String status, Instant claimedAt) {
    }

    public record MemberClaim(UUID claimId, UUID campaignId, UUID couponId, String campaignName, String couponName,
                              String description, String discountType, BigDecimal discountValue,
                              BigDecimal minimumOrderAmount, Instant startsAt, Instant endsAt, Instant claimedAt) {
    }

    public static class NotFound extends RuntimeException {
    }

    public static class Conflict extends RuntimeException {
        public final String code;

        public Conflict(String m, String c) {
            super(m);
            code = c;
        }
    }

    private static class ClaimFailure extends RuntimeException {
        final String code;
        final boolean notFound;

        ClaimFailure(String c, String m) {
            this(c, m, false);
        }

        ClaimFailure(String c, String m, boolean notFound) {
            super(m);
            code = c;
            this.notFound = notFound;
        }
    }
}
