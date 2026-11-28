package com.example.marketingplatform.analytics.application;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignAnalyticsService {
    private final CampaignAnalyticsQuery query;

    public CampaignAnalyticsService(CampaignAnalyticsQuery query) {
        this.query = query;
    }

    public Report get(UUID campaignId) {
        var summary = query.summary(campaignId);
        if (summary == null) throw new CampaignAnalyticsNotFoundException();
        var coupons = query.coupons(campaignId).stream().map(c -> new Coupon(c.couponId(), c.name(), c.totalQuantity(),
                c.successfulClaims(), c.remainingQuantity(), c.uniqueClaimers(),
                rate(c.uniqueClaimers(), summary.targetMembers()))).toList();
        return new Report(campaignId, summary.campaignName(), summary.targetMembers(), summary.deliveredMembers(),
                rate(summary.deliveredMembers(), summary.targetMembers()), summary.impressionCount(), summary.uniqueReach(),
                rate(summary.uniqueReach(), summary.targetMembers()), summary.clickCount(), summary.uniqueClickers(),
                rate(summary.uniqueClickers(), summary.deliveredMembers()), summary.landingPageViews(),
                summary.uniqueLandingVisitors(), rate(summary.uniqueLandingVisitors(), summary.targetMembers()),
                summary.couponClaims(), summary.uniqueClaimers(), rate(summary.uniqueClaimers(), summary.targetMembers()),
                summary.claimAttemptCount(), summary.failedClaimAttemptCount(),
                new Outcomes(summary.succeededAttempts(), summary.failedAttempts(), summary.replayedAttempts()), coupons);
    }

    private Rate rate(long numerator, long denominator) {
        return new Rate(numerator, denominator, denominator == 0 ? null : BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 8, java.math.RoundingMode.HALF_UP));
    }

    public record Rate(long numerator, long denominator, BigDecimal value) {
    }

    public record Outcomes(long succeeded, long failed, long replayed) {
    }

    public record Coupon(UUID couponId, String name, long totalQuantity, long successfulClaims,
                         long remainingQuantity, long uniqueClaimers, Rate targetClaimRate) {
    }

    public record Report(UUID campaignId, String campaignName, long targetMembers, long deliveredMembers,
                         Rate deliveryRate, long impressionCount, long uniqueReach, Rate reachRate,
                         long clickCount, long uniqueClickers, Rate clickRate, long landingPageViews,
                         long uniqueLandingVisitors, Rate targetLandingVisitorRate, long couponClaims,
                         long uniqueClaimers, Rate targetClaimRate, long claimAttemptCount,
                         long failedClaimAttemptCount, Outcomes claimAttemptsByOutcome, List<Coupon> coupons) {
    }

    public static class CampaignAnalyticsNotFoundException extends RuntimeException {
    }
}
