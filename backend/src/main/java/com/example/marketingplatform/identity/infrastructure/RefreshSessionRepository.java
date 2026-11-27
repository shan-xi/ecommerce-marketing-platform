package com.example.marketingplatform.identity.infrastructure;

import com.example.marketingplatform.identity.application.RefreshSessionStore;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshSessionRepository implements RefreshSessionStore {
    private final NamedParameterJdbcTemplate jdbc;

    public RefreshSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Session> findForUpdate(String hash) {
        return jdbc.query("""
                SELECT id, token_family_id, user_id, expires_at, consumed_at, revoked_at
                FROM refresh_token_sessions WHERE token_hash = :hash FOR UPDATE
                """, Map.of("hash", hash), rs -> rs.next() ? Optional.of(new Session(
                rs.getObject("id", UUID.class), rs.getObject("token_family_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant())) : Optional.empty());
    }

    public void insert(UUID id, UUID family, UUID userId, String hash, Instant expiresAt, String userAgent) {
        jdbc.update("""
                INSERT INTO refresh_token_sessions
                  (id, token_family_id, user_id, token_hash, expires_at, created_at, user_agent)
                VALUES (:id, :family, :userId, :hash, :expiresAt, :createdAt, :userAgent)
                """, Map.of("id", id, "family", family, "userId", userId, "hash", hash,
                "expiresAt", Timestamp.from(expiresAt), "createdAt", Timestamp.from(Instant.now()),
                "userAgent", userAgent == null ? "" : userAgent.substring(0, Math.min(500, userAgent.length()))));
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM refresh_token_sessions WHERE id = :id", Map.of("id", id));
    }

    public int consume(UUID id, UUID replacementId) {
        return jdbc.update("""
                UPDATE refresh_token_sessions SET consumed_at = :now, replaced_by_token_id = :replacement
                WHERE id = :id AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > :now
                """, Map.of("now", Timestamp.from(Instant.now()), "replacement", replacementId, "id", id));
    }

    public void revokeFamily(UUID family) {
        jdbc.update("UPDATE refresh_token_sessions SET revoked_at = :now WHERE token_family_id = :family AND revoked_at IS NULL",
                Map.of("now", Timestamp.from(Instant.now()), "family", family));
    }
}
