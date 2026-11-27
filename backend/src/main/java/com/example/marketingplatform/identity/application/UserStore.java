package com.example.marketingplatform.identity.application;

import com.example.marketingplatform.identity.domain.UserPrincipal;

import java.util.Optional;
import java.util.UUID;

public interface UserStore {
    Optional<UserRecord> findActiveByEmail(String email);

    Optional<UserPrincipal> findPrincipal(UUID id);

    record UserRecord(UUID id, String email, String passwordHash, String role) {
        public UserPrincipal principal() {
            return new UserPrincipal(id, role);
        }
    }
}
