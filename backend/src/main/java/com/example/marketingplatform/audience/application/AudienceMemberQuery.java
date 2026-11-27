package com.example.marketingplatform.audience.application;

import com.example.marketingplatform.audience.domain.AudienceRuleSet;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistence port for set-based audience matching.
 */
public interface AudienceMemberQuery {
    AudiencePreviewResult preview(List<AudienceRuleSet.Condition> conditions, LocalDate asOfDate, int sampleSize);

    List<java.util.UUID> findMemberIds(List<AudienceRuleSet.Condition> conditions, LocalDate asOfDate);
}
