package com.example.marketingplatform.audience;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.identity.infrastructure.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AudienceSegmentApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtTokenService tokens;

    @Test
    void adminCanCreateListReadUpdateAndPreviewSegments() throws Exception {
        UUID admin = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'ADMIN','ACTIVE',now(),now())", admin, "segment-admin-" + admin + "@example.test", "x");
        Cookie access = new Cookie("access_token", tokens.createAccessToken(new UserPrincipal(admin, "ADMIN")));
        mvc.perform(get("/api/v1/admin/audience-segments")).andExpect(status().isUnauthorized());
        UUID member = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role,status,created_at,updated_at) VALUES(?,?,?,'MEMBER','ACTIVE',now(),now())", member, "segment-member-" + member + "@example.test", "x");
        mvc.perform(get("/api/v1/admin/audience-segments").cookie(new Cookie("access_token", tokens.createAccessToken(new UserPrincipal(member, "MEMBER")))))
                .andExpect(status().isForbidden());
        String body = "{\"name\":\"Gold audience\",\"description\":\"Test segment\",\"rules\":{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"membershipTier\",\"operator\":\"=\",\"value\":\"GOLD\"}]}}";
        try {
            String response = mvc.perform(post("/api/v1/admin/audience-segments").cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value("Gold audience")).andReturn().getResponse().getContentAsString();
            java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(response);
            org.assertj.core.api.Assertions.assertThat(idMatcher.find()).isTrue();
            String id = idMatcher.group(1);
            mvc.perform(get("/api/v1/admin/audience-segments").cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
            mvc.perform(get("/api/v1/admin/audience-segments/" + id).cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.data.rules.conditions[0].field").value("membershipTier"));
            mvc.perform(put("/api/v1/admin/audience-segments/" + id).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body.replace("Gold audience", "Updated gold audience"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated gold audience"));
            mvc.perform(post("/api/v1/admin/audience-segments/preview").cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":1,\"match\":\"ALL\",\"conditions\":[{\"field\":\"age\",\"operator\":\">=\",\"value\":18}]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").isNumber()).andExpect(jsonPath("$.data.sample").isArray());
            mvc.perform(post("/api/v1/admin/audience-segments").cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body.replace("membershipTier", "unsupportedField"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AUDIENCE_RULE"));
        } finally {
            jdbc.update("DELETE FROM audience_segments WHERE created_by_user_id=?", admin);
            jdbc.update("DELETE FROM users WHERE id=?", member);
            jdbc.update("DELETE FROM users WHERE id=?", admin);
        }
    }
}
