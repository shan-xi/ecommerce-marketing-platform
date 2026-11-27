package com.example.marketingplatform.audience;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.audience.application.*;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AudienceEvaluationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    AudienceEvaluator evaluator;
    @Autowired
    AudienceRuleValidator validator;
    @Autowired
    JsonMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AudienceUseCase audienceUseCase;
    @Autowired
    AudienceSegmentService segmentService;

    @Test
    void evaluatesRulesAndCompositionWithNullsAsNonMatches() throws Exception {
        UUID user = UUID.randomUUID(), member = UUID.randomUUID(), secondUser = UUID.randomUUID(), secondMember = UUID.randomUUID(), nullUser = UUID.randomUUID(), nullMember = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'MEMBER','ACTIVE',now(),now()),(?,?,?,'MEMBER','ACTIVE',now(),now()),(?,?,?,'MEMBER','ACTIVE',now(),now())", user, "aud-test-" + user + "@example.test", "x", secondUser, "aud-test-" + secondUser + "@example.test", "x", nullUser, "aud-test-" + nullUser + "@example.test", "x");
        jdbc.update("INSERT INTO members(id,user_id,display_name,birth_date,gender,registered_at,membership_tier,order_count,total_spending,last_order_at,last_login_at,created_at,updated_at) VALUES(?,?,?,DATE '1990-01-01',NULL,now(),'GOLD',5,100.00,NULL,NULL,now(),now()),(?,?,?,DATE '1990-01-01','FEMALE',now(),'GOLD',1,10.00,NULL,NULL,now(),now()),(?,?,?,NULL,NULL,now(),'STANDARD',0,0,NULL,NULL,now(),now())", member, user, "Audience Test", secondMember, secondUser, "Audience Test Two", nullMember, nullUser, "Null Attribute Member");
        try {
            var gold = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"membershipTier\",\"operator\":\"=\",\"value\":\"GOLD\"}]}"));
            var genderNotFemale = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"gender\",\"operator\":\"!=\",\"value\":\"FEMALE\"}]}"));
            var genderNotIn = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"gender\",\"operator\":\"NOT IN\",\"value\":[\"FEMALE\"]}]}"));
            var result = evaluator.preview(gold, null, 10);
            assertThat(result.count()).isGreaterThanOrEqualTo(1);
            assertThat(result.sample()).extracting(AudiencePreviewResult.SampleMember::memberId).contains(member);
            assertThat(evaluator.preview(genderNotFemale, null, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).doesNotContain(member);
            assertThat(evaluator.preview(genderNotIn, null, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).doesNotContain(member);
            assertThatThrownBy(() -> evaluator.preview(null, null, 10)).isInstanceOf(IllegalArgumentException.class);
            var frequentOrders = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"orderCount\",\"operator\":\">=\",\"value\":5}]}"));
            assertThat(evaluator.preview(null, frequentOrders, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).contains(member).doesNotContain(secondMember);
            assertThat(evaluator.preview(gold, frequentOrders, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).contains(member).doesNotContain(secondMember);
            UUID admin = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'ADMIN','ACTIVE',now(),now())", admin, "aud-admin-" + admin + "@example.test", "x");
            try {
                var savedSegment = segmentService.create(admin, "Integration gold segment", null, mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"membershipTier\",\"operator\":\"=\",\"value\":\"GOLD\"}]}"));
                var composed = audienceUseCase.previewComposition(savedSegment.id(), mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"orderCount\",\"operator\":\">=\",\"value\":5}]}"));
                assertThat(composed.sample()).extracting(AudiencePreviewResult.SampleMember::memberId).contains(member).doesNotContain(secondMember);
                assertThatThrownBy(() -> audienceUseCase.previewComposition(null, null)).isInstanceOf(InvalidAudienceRuleException.class);
            } finally {
                jdbc.update("DELETE FROM audience_segments WHERE created_by_user_id=?", admin);
                jdbc.update("DELETE FROM users WHERE id=?", admin);
            }
            for (String field : new String[]{"age", "lastOrderAt", "lastLoginAt"}) {
                String scalar = field.equals("age") ? "0" : "\"2026-01-01T00:00:00Z\"";
                String ruleTemplate = "{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"%s\",\"operator\":\"%s\",\"value\":%s}]}";
                var notEqual = validator.validate(mapper.readTree(ruleTemplate.formatted(field, "!=", scalar)));
                var notIn = validator.validate(mapper.readTree(ruleTemplate.formatted(field, "NOT IN", "[" + scalar + "]")));
                assertThat(evaluator.preview(notEqual, null, 20).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).doesNotContain(nullMember);
                assertThat(evaluator.preview(notIn, null, 20).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).doesNotContain(nullMember);
            }
        } finally {
            jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", member, secondMember, nullMember);
            jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", user, secondUser, nullUser);
        }
    }
}
