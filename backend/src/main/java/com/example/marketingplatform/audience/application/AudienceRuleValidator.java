package com.example.marketingplatform.audience.application;

import com.example.marketingplatform.audience.domain.AudienceRuleSet;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AudienceRuleValidator {
    private static final Set<String> COMPARISONS = Set.of("=", "!=", ">", ">=", "<", "<=");
    private static final Set<String> SETS = Set.of("IN", "NOT IN");
    private static final Map<String, FieldSpec> FIELDS = Map.of(
            "age", new FieldSpec(Kind.INTEGER, null),
            "gender", new FieldSpec(Kind.ENUM, Set.of("FEMALE", "MALE", "OTHER", "UNDISCLOSED")),
            "registeredAt", new FieldSpec(Kind.DATETIME, null),
            "membershipTier", new FieldSpec(Kind.ENUM, Set.of("STANDARD", "SILVER", "GOLD", "PLATINUM")),
            "orderCount", new FieldSpec(Kind.INTEGER, null),
            "totalSpending", new FieldSpec(Kind.DECIMAL, null),
            "lastOrderAt", new FieldSpec(Kind.DATETIME, null),
            "lastLoginAt", new FieldSpec(Kind.DATETIME, null));

    public AudienceRuleSet validate(JsonNode root) {
        if (root == null || !root.isObject() || root.size() != 3
                || !root.has("version") || !root.has("match") || !root.has("conditions"))
            throw invalid("Rule document must contain only version, match and conditions.");
        if (!root.get("version").isIntegralNumber() || root.get("version").intValue() != 1)
            throw invalid("Only rule version 1 is supported.");
        if (!root.get("match").isTextual() || !"ALL".equals(root.get("match").textValue()))
            throw invalid("match must be ALL.");
        JsonNode conditions = root.get("conditions");
        if (!conditions.isArray() || conditions.isEmpty()) throw invalid("conditions must be a non-empty array.");
        List<AudienceRuleSet.Condition> result = new ArrayList<>();
        for (JsonNode condition : conditions) {
            if (!condition.isObject() || condition.size() != 3 || !condition.has("field")
                    || !condition.has("operator") || !condition.has("value"))
                throw invalid("Each condition must contain only field, operator and value.");
            if (!condition.get("field").isTextual() || !condition.get("operator").isTextual())
                throw invalid("field and operator must be strings.");
            String field = condition.get("field").textValue();
            String operator = condition.get("operator").textValue();
            FieldSpec spec = FIELDS.get(field);
            if (spec == null) throw invalid("Unsupported field: " + field);
            if (!(COMPARISONS.contains(operator) || SETS.contains(operator)))
                throw invalid("Unsupported operator: " + operator);
            if (spec.kind == Kind.ENUM && !Set.of("=", "!=", "IN", "NOT IN").contains(operator))
                throw invalid("Ordered comparisons are not supported for enum fields.");
            JsonNode value = condition.get("value");
            Object normalized;
            if (SETS.contains(operator)) {
                if (!value.isArray() || value.isEmpty()) throw invalid("IN and NOT IN require a non-empty array.");
                List<Object> values = new ArrayList<>();
                for (JsonNode item : value) values.add(parseValue(field, spec, item));
                normalized = List.copyOf(values);
            } else {
                if (value.isArray() || value.isNull() || value.isObject())
                    throw invalid("Comparison operators require one non-null scalar.");
                normalized = parseValue(field, spec, value);
            }
            result.add(new AudienceRuleSet.Condition(field, operator, normalized));
        }
        return new AudienceRuleSet(1, "ALL", List.copyOf(result));
    }

    private Object parseValue(String field, FieldSpec spec, JsonNode value) {
        if (value == null || value.isNull() || value.isBoolean() || value.isArray() || value.isObject())
            throw invalid("NULL, boolean, object and nested array values are not supported.");
        try {
            return switch (spec.kind) {
                case INTEGER -> {
                    if (!value.isIntegralNumber() || !value.canConvertToLong())
                        throw invalid(field + " requires an integer.");
                    long number = value.longValue();
                    if (number < 0 || ("age".equals(field) && number > 120)) throw invalid(field + " is out of range.");
                    yield Math.toIntExact(number);
                }
                case DECIMAL -> {
                    if (!value.isNumber()) throw invalid(field + " requires a decimal number.");
                    BigDecimal number = value.decimalValue();
                    if (number.signum() < 0 || number.scale() > 2)
                        throw invalid(field + " must be >= 0 with at most 2 decimal places.");
                    yield number;
                }
                case ENUM -> {
                    if (!value.isTextual()) throw invalid(field + " requires a string enum value.");
                    String text = value.textValue();
                    if (!spec.values.contains(text)) throw invalid("Invalid " + field + " enum value.");
                    yield text;
                }
                case DATETIME -> {
                    if (!value.isTextual()) throw invalid(field + " requires an ISO-8601 offset timestamp.");
                    yield OffsetDateTime.parse(value.textValue()).withOffsetSameInstant(java.time.ZoneOffset.UTC);
                }
            };
        } catch (DateTimeParseException | ArithmeticException ex) {
            throw invalid("Invalid value for " + field + ".");
        }
    }

    private InvalidAudienceRuleException invalid(String message) {
        return new InvalidAudienceRuleException(message);
    }

    private enum Kind {INTEGER, DECIMAL, ENUM, DATETIME}

    private record FieldSpec(Kind kind, Set<String> values) {
    }
}
