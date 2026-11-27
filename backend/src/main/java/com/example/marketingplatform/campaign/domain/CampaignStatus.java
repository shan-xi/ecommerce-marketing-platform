package com.example.marketingplatform.campaign.domain;

import java.time.Instant;

/**
 * Persisted lifecycle states plus ENDED as a read-only derived status.
 */
public enum CampaignStatus {
    DRAFT, ACTIVE, PAUSED, ENDED;

    public static CampaignStatus effectiveReadStatus(String persistedStatus, Instant publishedAt,
                                                     Instant endsAt, Instant now) {
        CampaignStatus persisted = CampaignStatus.valueOf(persistedStatus);
        if (persisted == DRAFT) return DRAFT;
        if (publishedAt != null && endsAt != null && !endsAt.isAfter(now)) return ENDED;
        return persisted;
    }
}
