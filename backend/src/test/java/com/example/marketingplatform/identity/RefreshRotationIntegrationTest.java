package com.example.marketingplatform.identity;

import com.example.marketingplatform.AbstractPostgresIntegrationTest;
import com.example.marketingplatform.identity.application.IdentityService;
import com.example.marketingplatform.identity.application.RefreshRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshRotationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    IdentityService identity;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void onlyOneConcurrentRefreshConsumesGenerationAndReuseRevokesFamily() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', now(), now())
                """, userId, "refresh-race-" + userId + "@example.test", passwordEncoder.encode("Test-password-123!"));

        var login = identity.login("refresh-race-" + userId + "@example.test", "Test-password-123!", "test");
        String original = login.refreshToken();
        String originalHash = sha256(original);
        UUID familyId = jdbc.queryForObject(
                "SELECT token_family_id FROM refresh_token_sessions WHERE token_hash = ?",
                UUID.class, originalHash);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        identity.refresh(original, "parallel-test");
                        successes.incrementAndGet();
                    } catch (RefreshRejectedException ex) {
                        rejected.incrementAndGet();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            ready.await();
            start.countDown();
            executor.shutdown();
            while (!executor.isTerminated()) Thread.sleep(10);
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        Integer activeGenerations = jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE token_family_id = ? AND revoked_at IS NULL
                """, Integer.class, familyId);
        assertThat(activeGenerations).isZero();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void expiredRefreshSessionIsRejectedAndRevokesItsFamily() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'MEMBER', 'ACTIVE', now(), now())
                        """, userId, "refresh-expired-" + userId + "@example.test",
                passwordEncoder.encode("Test-password-123!"));

        var login = identity.login("refresh-expired-" + userId + "@example.test",
                "Test-password-123!", "expired-refresh-test");
        String original = login.refreshToken();
        String originalHash = sha256(original);
        UUID familyId = jdbc.queryForObject(
                "SELECT token_family_id FROM refresh_token_sessions WHERE token_hash = ?",
                UUID.class, originalHash);
        jdbc.update("UPDATE refresh_token_sessions SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                originalHash);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> identity.refresh(original, "expired-refresh-test"))
                .isInstanceOf(RefreshRejectedException.class);

        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_token_sessions
                WHERE token_family_id = ? AND revoked_at IS NULL
                """, Integer.class, familyId);
        assertThat(active).isZero();
    }

    @Test
    void unknownRefreshTokenIsRejectedWithoutCreatingASession() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> identity.refresh("unknown-refresh-token", "unknown-refresh-test"))
                .isInstanceOf(RefreshRejectedException.class);
    }
}
