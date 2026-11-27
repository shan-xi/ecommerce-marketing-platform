package com.example.marketingplatform.identity;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginRefreshAndLogoutUseHttpOnlyCookiesAndLogoutDoesNotRequireAccessCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "auth-flow-" + userId + "@example.test";
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', now(), now())
                """, userId, email, passwordEncoder.encode("Correct-password-123!"));

        Csrf csrf = csrf();
        var login = mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header("X-CSRF-TOKEN", csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Correct-password-123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();

        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/api/v1");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_token_sessions WHERE user_id = ?", String.class, userId);
        assertThat(storedHash).hasSize(64).isNotEqualTo(refresh.getValue());

        mvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));

        var rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.cookie(), refresh)
                        .header("X-CSRF-TOKEN", csrf.value()))
                .andExpect(status().isOk()).andReturn();
        Cookie nextRefresh = rotated.getResponse().getCookie("refresh_token");
        assertThat(rotated.getResponse().getCookie("access_token")).isNotNull();
        assertThat(nextRefresh).isNotNull();
        assertThat(nextRefresh.getValue()).isNotEqualTo(refresh.getValue());
        Integer consumed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ? AND consumed_at IS NOT NULL",
                Integer.class, userId);
        assertThat(consumed).isEqualTo(1);

        mvc.perform(post("/api/v1/auth/logout").cookie(csrf.cookie(), nextRefresh)
                        .header("X-CSRF-TOKEN", csrf.value()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, userId);
        assertThat(active).isZero();
    }

    private Csrf csrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"csrfToken\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("CSRF token missing from response");
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), matcher.group(1));
    }

    private record Csrf(Cookie cookie, String value) {
    }
}
