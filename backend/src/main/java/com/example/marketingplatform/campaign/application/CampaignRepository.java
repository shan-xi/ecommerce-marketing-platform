package com.example.marketingplatform.campaign.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository {
    void create(CampaignRow row);

    Optional<CampaignRow> findById(UUID id);

    List<CampaignRow> findPage(int page, int size, String status, Instant now);

    long count(String status, Instant now);

    UpdateResult updateDraft(CampaignRow row);

    enum UpdateResult {UPDATED, NOT_FOUND, NOT_DRAFT}

    record CampaignRow(UUID id, String name, String description, String headline, String body,
                       String status, Instant startsAt, Instant endsAt, UUID segmentId,
                       String rulesJson, Instant publishedAt, UUID createdByUserId,
                       Instant createdAt, Instant updatedAt) {
    }
}
