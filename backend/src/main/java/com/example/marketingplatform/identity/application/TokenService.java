package com.example.marketingplatform.identity.application;

import com.example.marketingplatform.identity.domain.UserPrincipal;

import java.time.Instant;
import java.util.UUID;

public interface TokenService {
    String createAccessToken(UserPrincipal principal);

    String createRefreshToken(UserPrincipal principal);

    ParsedToken parse(String serializedToken);

    record ParsedToken(UUID tokenId, UUID userId, String tokenUse, Instant expiresAt) {
    }
}
