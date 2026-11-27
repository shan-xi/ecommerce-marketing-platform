package com.example.marketingplatform.audience.application;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Application-level boundary for later modules to request audience evaluation without accessing repositories.
 */
public interface AudienceUseCase {
    AudiencePreviewResult preview(JsonNode rules);

    AudiencePreviewResult previewComposition(UUID savedSegmentId, JsonNode campaignSpecificRules);

    void validateComposition(UUID savedSegmentId, JsonNode campaignSpecificRules);
}
