package com.example.marketingplatform.analytics.application;

import java.util.List;
import java.util.UUID;

/**
 * Read port for campaign analytics; persistence details stay in the infrastructure adapter.
 */
public interface CampaignAnalyticsQuery {
    Summary summary(UUID campaignId);

    List<CouponSummary> coupons(UUID campaignId);

    record Summary(String campaignName, long targetMembers, long deliveredMembers, long impressionCount,
                   long uniqueReach, long clickCount, long uniqueClickers, long landingPageViews,
                   long uniqueLandingVisitors, long couponClaims, long uniqueClaimers, long claimAttemptCount,
                   long failedClaimAttemptCount, long succeededAttempts, long failedAttempts, long replayedAttempts) {
    }

    record CouponSummary(UUID couponId, String name, long totalQuantity, long successfulClaims,
                         long remainingQuantity, long uniqueClaimers) {
    }
}
