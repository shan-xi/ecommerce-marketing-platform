package com.example.marketingplatform.audience.application;

import com.example.marketingplatform.audience.domain.AudienceRuleSet;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class AudienceEvaluator {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private final AudienceMemberQuery memberQuery;
    private final Clock clock;

    public AudienceEvaluator(AudienceMemberQuery memberQuery, Clock clock) {
        this.memberQuery = memberQuery;
        this.clock = clock;
    }

    public List<java.util.UUID> resolveMemberIds(AudienceRuleSet segmentRules, AudienceRuleSet campaignRules) {
        if (segmentRules == null && campaignRules == null)
            throw new IllegalArgumentException("At least one audience definition is required.");
        List<AudienceRuleSet.Condition> conditions = new ArrayList<>();
        if (segmentRules != null) conditions.addAll(segmentRules.conditions());
        if (campaignRules != null) conditions.addAll(campaignRules.conditions());
        LocalDate asOfDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return memberQuery.findMemberIds(List.copyOf(conditions), asOfDate);
    }

    public AudiencePreviewResult preview(AudienceRuleSet segmentRules, AudienceRuleSet campaignRules, int sampleSize) {
        if (segmentRules == null && campaignRules == null)
            throw new IllegalArgumentException("At least one audience definition is required.");
        List<AudienceRuleSet.Condition> conditions = new ArrayList<>();
        if (segmentRules != null) conditions.addAll(segmentRules.conditions());
        if (campaignRules != null) conditions.addAll(campaignRules.conditions());
        LocalDate asOfDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return memberQuery.preview(List.copyOf(conditions), asOfDate, sampleSize);
    }

}
