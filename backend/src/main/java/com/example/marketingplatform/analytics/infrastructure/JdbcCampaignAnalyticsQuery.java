package com.example.marketingplatform.analytics.infrastructure;

import com.example.marketingplatform.analytics.application.CampaignAnalyticsIntegrityException;
import com.example.marketingplatform.analytics.application.CampaignAnalyticsQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCampaignAnalyticsQuery implements CampaignAnalyticsQuery {
    private final JdbcTemplate jdbc;

    public JdbcCampaignAnalyticsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Summary summary(UUID campaignId) {
        var campaigns = jdbc.query("SELECT name FROM campaigns WHERE id=?", (rs, n) -> rs.getString(1), campaignId);
        if (campaigns.isEmpty()) return null;
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM campaign_audience_members WHERE campaign_id=:campaignId) AS target_members,
                  (SELECT COUNT(*) FROM member_messages WHERE campaign_id=:campaignId) AS delivered_members,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='IMPRESSION') AS impression_count,
                  (SELECT COUNT(DISTINCT member_id) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='IMPRESSION') AS unique_reach,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLICK') AS click_count,
                  (SELECT COUNT(DISTINCT member_id) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLICK') AS unique_clickers,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='LANDING_PAGE_VIEW') AS landing_page_views,
                  (SELECT COUNT(DISTINCT e.member_id) FROM campaign_events e JOIN campaign_audience_members a
                    ON a.campaign_id=e.campaign_id AND a.member_id=e.member_id
                    WHERE e.campaign_id=:campaignId AND e.event_type='LANDING_PAGE_VIEW') AS unique_landing_visitors,
                  (SELECT COUNT(*) FROM coupon_claims WHERE campaign_id=:campaignId) AS coupon_claims,
                  (SELECT COUNT(DISTINCT member_id) FROM coupon_claims WHERE campaign_id=:campaignId) AS unique_claimers,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLAIM_ATTEMPT') AS claim_attempt_count,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLAIM_ATTEMPT' AND metadata->>'outcome'='FAILED') AS failed_claim_attempt_count,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLAIM_ATTEMPT' AND metadata->>'outcome'='SUCCEEDED') AS succeeded_attempts,
                  (SELECT COUNT(*) FROM campaign_events WHERE campaign_id=:campaignId AND event_type='CLAIM_ATTEMPT' AND metadata->>'outcome'='REPLAYED') AS replayed_attempts
                """;
        var p = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("campaignId", campaignId);
        var row = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).queryForObject(sql, p, (rs, n) ->
                new Summary(campaigns.getFirst(), rs.getLong("target_members"), rs.getLong("delivered_members"),
                        rs.getLong("impression_count"), rs.getLong("unique_reach"), rs.getLong("click_count"),
                        rs.getLong("unique_clickers"), rs.getLong("landing_page_views"), rs.getLong("unique_landing_visitors"),
                        rs.getLong("coupon_claims"), rs.getLong("unique_claimers"), rs.getLong("claim_attempt_count"),
                        rs.getLong("failed_claim_attempt_count"), rs.getLong("succeeded_attempts"),
                        rs.getLong("failed_claim_attempt_count"), rs.getLong("replayed_attempts")));
        return row;
    }

    @Override
    public List<CouponSummary> coupons(UUID campaignId) {
        String sql = """
                SELECT c.id, c.name, c.total_quantity, c.claimed_quantity,
                       COUNT(cl.id) AS successful_claims, COUNT(DISTINCT cl.member_id) AS unique_claimers
                FROM campaign_coupons c
                LEFT JOIN coupon_claims cl ON cl.campaign_id=c.campaign_id AND cl.campaign_coupon_id=c.id
                WHERE c.campaign_id=?
                GROUP BY c.id, c.name, c.total_quantity, c.claimed_quantity
                ORDER BY c.created_at, c.id
                """;
        return jdbc.query(sql, (rs, n) -> {
            long total = rs.getLong("total_quantity");
            long claimedCounter = rs.getLong("claimed_quantity");
            long claims = rs.getLong("successful_claims");
            if (claimedCounter != claims) throw new CampaignAnalyticsIntegrityException(rs.getObject("id", UUID.class));
            return new CouponSummary(rs.getObject("id", UUID.class), rs.getString("name"), total,
                    claims, total - claimedCounter, rs.getLong("unique_claimers"));
        }, campaignId);
    }


}
