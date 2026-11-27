package com.example.marketingplatform.audience;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.audience.application.AudienceEvaluator;
import com.example.marketingplatform.audience.application.AudiencePreviewResult;
import com.example.marketingplatform.audience.application.AudienceRuleValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(AudienceAgeTimezoneIntegrationTest.FixedClockConfiguration.class)
class AudienceAgeTimezoneIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    AudienceEvaluator evaluator;
    @Autowired
    AudienceRuleValidator validator;
    @Autowired
    JsonMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ageUsesTaipeiCalendarDateRatherThanUtcDate() throws Exception {
        UUID user = UUID.randomUUID(), member = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'MEMBER','ACTIVE',now(),now())", user, "age-test-" + user + "@example.test", "x");
        jdbc.update("INSERT INTO members(id,user_id,display_name,birth_date,gender,registered_at,membership_tier,order_count,total_spending,created_at,updated_at) VALUES(?,?,?,DATE '2000-11-28','FEMALE',now(),'GOLD',0,0,now(),now())", member, user, "Age Boundary");
        try {
            var age26 = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"age\",\"operator\":\"=\",\"value\":26}]}"));
            var age25 = validator.validate(mapper.readTree("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"age\",\"operator\":\"=\",\"value\":25}]}"));
            assertThat(evaluator.preview(age26, null, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).contains(member);
            assertThat(evaluator.preview(age25, null, 10).sample()).extracting(AudiencePreviewResult.SampleMember::memberId).doesNotContain(member);
        } finally {
            jdbc.update("DELETE FROM members WHERE id=?", member);
            jdbc.update("DELETE FROM users WHERE id=?", user);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-27T16:30:00Z"), ZoneOffset.UTC);
        }
    }
}
