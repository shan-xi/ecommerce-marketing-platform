package com.example.marketingplatform.audience;

import com.example.marketingplatform.audience.application.AudienceRuleValidator;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceRuleValidatorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private AudienceRuleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AudienceRuleValidator();
    }

    @Test
    void acceptsAllEightFieldsAndTheirSupportedOperatorFamilies() throws Exception {
        String[] valid = {
                "{\"field\":\"age\",\"operator\":\"=\",\"value\":18}",
                "{\"field\":\"age\",\"operator\":\">=\",\"value\":18}",
                "{\"field\":\"gender\",\"operator\":\"IN\",\"value\":[\"FEMALE\",\"OTHER\"]}",
                "{\"field\":\"registeredAt\",\"operator\":\">\",\"value\":\"2026-01-01T00:00:00Z\"}",
                "{\"field\":\"membershipTier\",\"operator\":\"!=\",\"value\":\"GOLD\"}",
                "{\"field\":\"orderCount\",\"operator\":\"NOT IN\",\"value\":[0,2]}",
                "{\"field\":\"totalSpending\",\"operator\":\"<=\",\"value\":123.45}",
                "{\"field\":\"lastOrderAt\",\"operator\":\"=\",\"value\":\"2026-01-01T00:00:00+08:00\"}",
                "{\"field\":\"lastLoginAt\",\"operator\":\"NOT IN\",\"value\":[\"2026-01-01T00:00:00Z\"]}"
        };
        for (String condition : valid)
            validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[" + condition + "]}"));
    }

    @Test
    void rejectsUnknownFieldsOperatorsWrongTypesAndNulls() {
        assertInvalid("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"email\",\"operator\":\"=\",\"value\":\"a\"}]}");
        assertInvalid(rule("gender", ">", "\"FEMALE\""));
        assertInvalid(rule("age", "=", "\"18\""));
        assertInvalid(rule("age", "=", "null"));
        assertInvalid(rule("age", "IN", "[]"));
        assertInvalid(rule("age", "IN", "[1,null]"));
        assertInvalid(rule("age", "=", "true"));
        assertInvalid(rule("age", "LIKE", "18"));
        assertInvalid(rule("age", "=", "121"));
        assertInvalid(rule("orderCount", "=", "2147483648"));
        assertInvalid("{\"version\":1,\"match\":\"ALL\",\"extra\":true,\"conditions\":[{\"field\":\"age\",\"operator\":\"=\",\"value\":18}]}");
        assertInvalid("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"age\",\"operator\":\"=\",\"value\":18,\"extra\":true}]}");
        assertInvalid(rule("orderCount", "=", "-1"));
        assertInvalid(rule("totalSpending", "=", "1.234"));
        assertInvalid(rule("gender", "=", "\"female\""));
        assertInvalid(rule("registeredAt", "=", "\"2026-01-01\""));
    }

    @Test
    void acceptsEveryComparisonAndSetOperatorForNumericFields() {
        for (String field : new String[]{"age", "orderCount", "totalSpending"}) {
            for (String op : new String[]{"=", "!=", ">", ">=", "<", "<=", "IN", "NOT IN"}) {
                String value = op.equals("IN") || op.equals("NOT IN") ? "[2,3]" : "2";
                validator.validate(parse(rule(field, op, value)));
            }
        }
    }

    @Test
    void acceptsEveryAllowedOperatorForEnumAndDateFields() {
        for (String field : new String[]{"gender", "membershipTier"}) {
            for (String op : new String[]{"=", "!=", "IN", "NOT IN"}) {
                String one = field.equals("gender") ? "FEMALE" : "GOLD";
                String two = field.equals("gender") ? "OTHER" : "SILVER";
                String value = op.equals("IN") || op.equals("NOT IN") ? "[\"" + one + "\",\"" + two + "\"]" : "\"" + one + "\"";
                validator.validate(parse(rule(field, op, value)));
            }
        }
        for (String field : new String[]{"registeredAt", "lastOrderAt", "lastLoginAt"}) {
            for (String op : new String[]{"=", "!=", ">", ">=", "<", "<=", "IN", "NOT IN"}) {
                String one = "\"2026-01-01T00:00:00Z\"";
                String value = op.equals("IN") || op.equals("NOT IN") ? "[" + one + "]" : one;
                validator.validate(parse(rule(field, op, value)));
            }
        }
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> validator.validate(parse(json))).isInstanceOf(InvalidAudienceRuleException.class);
    }

    private tools.jackson.databind.JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String rule(String field, String op, String value) {
        return "{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"" + field + "\",\"operator\":\"" + op + "\",\"value\":" + value + "}]}";
    }
}
