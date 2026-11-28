package com.example.marketingplatform.audience.application;

import java.util.UUID;

/**
 * Read-only application boundary for publication-time audience snapshot membership.
 */
public interface AudienceSnapshotQuery {
    boolean containsMember(UUID campaignId, UUID memberId);
}
