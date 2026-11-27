package com.example.marketingplatform.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionStore {
    Optional<Session> findForUpdate(String tokenHash);

    void insert(UUID id, UUID familyId, UUID userId, String tokenHash, Instant expiresAt, String userAgent);

    void delete(UUID id);

    int consume(UUID id, UUID replacementId);

    void revokeFamily(UUID familyId);

    record Session(UUID id, UUID familyId, UUID userId, Instant expiresAt,
                   Instant consumedAt, Instant revokedAt) {
    }
}
