package com.example.marketingplatform.analytics.application;

import java.util.UUID;

public class CampaignAnalyticsIntegrityException extends RuntimeException {
    public CampaignAnalyticsIntegrityException(UUID couponId) {
        super("Coupon inventory counter does not match successful claims: " + couponId);
    }
}
