package com.example.marketingplatform.identity.infrastructure;

import com.example.marketingplatform.identity.application.UserStore;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository implements UserStore {
    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findActiveByEmail(String email) {
        return jdbc.query("""
                SELECT id, email, password_hash, role FROM users
                WHERE lower(email) = :email AND status = 'ACTIVE'
                """, Map.of("email", email.trim().toLowerCase(Locale.ROOT)), rs ->
                rs.next() ? Optional.of(new UserRecord(rs.getObject("id", UUID.class),
                        rs.getString("email"), rs.getString("password_hash"), rs.getString("role"))) : Optional.empty());
    }

    public Optional<UserPrincipal> findPrincipal(UUID id) {
        return jdbc.query("SELECT id, role FROM users WHERE id = :id AND status = 'ACTIVE'",
                Map.of("id", id), rs -> rs.next()
                        ? Optional.of(new UserPrincipal(rs.getObject("id", UUID.class), rs.getString("role")))
                        : Optional.empty());
    }
}
