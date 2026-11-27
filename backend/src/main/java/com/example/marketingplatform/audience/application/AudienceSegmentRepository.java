package com.example.marketingplatform.audience.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for reusable audience segments.
 */
public interface AudienceSegmentRepository {
    void create(UUID id, String name, String description, String rulesJson, UUID createdByUserId, Instant now);

    boolean update(UUID id, String name, String description, String rulesJson, Instant now);

    Optional<SegmentRow> findById(UUID id);

    long countActive();

    List<SegmentRow> findPage(int page, int size);

    record SegmentRow(UUID id, String name, String description, String rulesJson, UUID createdByUserId,
                      Instant createdAt, Instant updatedAt) {
    }
}
