package com.example.marketingplatform.audience.domain;

import java.util.List;

public record AudienceRuleSet(int version, String match, List<Condition> conditions) {
    public record Condition(String field, String operator, Object value) {
    }
}
