package com.example.marketingplatform.identity;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtTokenService tokens;

    @Test
    void anonymousCannotAccessCurrentPrincipalOrProtectedRoleGroups() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/member/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void memberAndAdminRolesCannotCrossTheirRouteBoundaries() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', now(), now()),
                               (?, ?, ?, 'ADMIN', 'ACTIVE', now(), now())
                        """, memberId, "role-member-" + memberId + "@example.test", passwordEncoder.encode("x"),
                adminId, "role-admin-" + adminId + "@example.test", passwordEncoder.encode("x"));

        String memberAccess = tokens.createAccessToken(new UserPrincipal(memberId, "MEMBER"));
        String adminAccess = tokens.createAccessToken(new UserPrincipal(adminId, "ADMIN"));

        mvc.perform(get("/api/v1/admin/probe").cookie(new Cookie("access_token", memberAccess)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/member/probe").cookie(new Cookie("access_token", adminAccess)))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointInitializesReadableTokenAndLoginRequiresIt() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"bad\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty()).andReturn();
        String body = result.getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"csrfToken\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        String csrf = matcher.group(1);

        mvc.perform(post("/api/v1/auth/login").cookie(result.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-CSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutIsCsrfProtected() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    void refreshAndLogoutRequireCsrfHeaderEvenWhenCookiesArePresent() throws Exception {
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", "test-csrf-cookie");
        Cookie refreshCookie = new Cookie("refresh_token", "not-a-real-token");

        mvc.perform(post("/api/v1/auth/refresh").cookie(csrfCookie, refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mvc.perform(post("/api/v1/auth/logout").cookie(csrfCookie, refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void accessTokenWithInvalidSignatureIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', now(), now())
                """, userId, "bad-signature-" + userId + "@example.test", passwordEncoder.encode("x"));

        String valid = tokens.createAccessToken(new UserPrincipal(userId, "ADMIN"));
        int signatureStart = valid.lastIndexOf('.') + 1;
        char firstSignatureChar = valid.charAt(signatureStart);
        String tampered = valid.substring(0, signatureStart)
                + (firstSignatureChar == 'A' ? 'B' : 'A')
                + valid.substring(signatureStart + 1);

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", tampered)))
                .andExpect(status().isUnauthorized());
    }
}
