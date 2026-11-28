package com.example.marketingplatform.coupon.infrastructure;

import com.example.marketingplatform.coupon.application.CouponQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcCouponQuery implements CouponQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCouponQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublicCouponRow> findPublicCoupons(UUID campaignId, UUID memberId, long offset, int limit) {
        return jdbc.query("""
                SELECT cc.id,cc.name,cc.description,cc.discount_type,cc.discount_value,cc.minimum_order_amount,
                    cc.total_quantity,cc.claimed_quantity,cc.starts_at,cc.ends_at,
                    EXISTS(SELECT 1 FROM coupon_claims cl WHERE cl.campaign_id=cc.campaign_id
                        AND cl.campaign_coupon_id=cc.id AND cl.member_id=CAST(:member AS uuid)) AS already_claimed
                FROM campaign_coupons cc WHERE cc.campaign_id=:campaign
                ORDER BY cc.created_at ASC,cc.id ASC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource().addValue("campaign", campaignId).addValue("member", memberId)
                .addValue("limit", limit).addValue("offset", offset), (r, n) -> new PublicCouponRow(
                r.getObject("id", UUID.class), r.getString("name"), r.getString("description"),
                r.getString("discount_type"), r.getBigDecimal("discount_value"), r.getBigDecimal("minimum_order_amount"),
                r.getInt("total_quantity"), r.getInt("claimed_quantity"), r.getTimestamp("starts_at").toInstant(),
                r.getTimestamp("ends_at").toInstant(), r.getBoolean("already_claimed")));
    }

    @Override
    public long countPublicCoupons(UUID campaignId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM campaign_coupons WHERE campaign_id=:campaign",
                Map.of("campaign", campaignId), Long.class);
        return count == null ? 0 : count;
    }
}
