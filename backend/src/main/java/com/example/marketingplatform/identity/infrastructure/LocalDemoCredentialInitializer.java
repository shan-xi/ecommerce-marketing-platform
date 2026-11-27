package com.example.marketingplatform.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optional local-only helper. No demo password is committed to source control or a migration.
 * Set DEMO_SEED_PASSWORD explicitly in the untracked local .env file to enable it.
 */
@Component
@Profile("local")
public class LocalDemoCredentialInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public LocalDemoCredentialInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
                                          @Value("${app.security.demo-seed-password:}") String demoPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) return;
        if (demoPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("DEMO_SEED_PASSWORD must be at most 72 UTF-8 bytes for BCrypt");
        }
        String hash = encoder.encode(demoPassword);
        jdbc.update("""
                UPDATE users SET password_hash = ?, updated_at = now()
                WHERE email IN ('admin@example.test', 'member.one@example.test',
                                'member.two@example.test', 'member.three@example.test')
                """, hash);
    }
}
