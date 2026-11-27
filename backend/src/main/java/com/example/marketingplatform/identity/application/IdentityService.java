package com.example.marketingplatform.identity.application;

import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IdentityService {
    private final UserStore users;
    private final RefreshSessionStore sessions;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final String dummyPasswordHash;

    public IdentityService(UserStore users, RefreshSessionStore sessions,
                           PasswordEncoder passwordEncoder, TokenService tokens,
                           @Value("${app.security.access-token-ttl:PT15M}") java.time.Duration accessTtl,
                           @Value("${app.security.refresh-token-ttl:P7D}") java.time.Duration refreshTtl) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.accessTtlSeconds = accessTtl.toSeconds();
        this.refreshTtlSeconds = refreshTtl.toSeconds();
        if (this.accessTtlSeconds <= 0 || this.refreshTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT token TTLs must be positive");
        }
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public IssuedTokens login(String email, String password, String userAgent) {
        var user = users.findActiveByEmail(email);
        // Perform a BCrypt check for unknown/inactive accounts as well to reduce timing-based enumeration.
        String candidateHash = user.map(UserStore.UserRecord::passwordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(password, candidateHash);
        if (user.isEmpty() || !matches) throw new AuthenticationException();

        UserPrincipal principal = user.get().principal();
        UUID familyId = UUID.randomUUID();
        String refresh = tokens.createRefreshToken(principal);
        TokenService.ParsedToken refreshClaims = tokens.parse(refresh);
        UUID tokenId = refreshClaims.tokenId();
        sessions.insert(tokenId, familyId, principal.userId(), sha256(refresh),
                refreshClaims.expiresAt(), userAgent);
        return new IssuedTokens(tokens.createAccessToken(principal), refresh, principal,
                accessTtlSeconds, refreshTtlSeconds);
    }

    @Transactional(noRollbackFor = RefreshRejectedException.class)
    public IssuedTokens refresh(String serializedRefresh, String userAgent) {
        if (serializedRefresh == null || serializedRefresh.isBlank()) throw new RefreshRejectedException();

        // The stored hash is an exact-token lookup. It also lets us detect reuse of an
        // expired-but-previously-issued generation, even though JWT expiry validation fails.
        var sessionOpt = sessions.findForUpdate(sha256(serializedRefresh));
        if (sessionOpt.isEmpty()) throw new RefreshRejectedException();
        var session = sessionOpt.get();
        if (session.consumedAt() != null || session.revokedAt() != null || !session.expiresAt().isAfter(Instant.now())) {
            sessions.revokeFamily(session.familyId());
            throw new RefreshRejectedException();
        }

        TokenService.ParsedToken parsed;
        try {
            parsed = tokens.parse(serializedRefresh);
            if (!"refresh".equals(parsed.tokenUse())) throw new IllegalArgumentException("Wrong token purpose");
        } catch (RuntimeException ex) {
            sessions.revokeFamily(session.familyId());
            throw new RefreshRejectedException();
        }

        UUID tokenId = parsed.tokenId();
        UUID userId = parsed.userId();
        if (!session.id().equals(tokenId) || !session.userId().equals(userId)) {
            sessions.revokeFamily(session.familyId());
            throw new RefreshRejectedException();
        }
        var principal = users.findPrincipal(userId).orElse(null);
        if (principal == null) {
            sessions.revokeFamily(session.familyId());
            throw new RefreshRejectedException();
        }

        String nextRefresh = tokens.createRefreshToken(principal);
        TokenService.ParsedToken nextClaims = tokens.parse(nextRefresh);
        UUID nextId = nextClaims.tokenId();
        // Insert the replacement first so the predecessor's self-referencing FK is valid.
        // Both operations remain in this transaction and are invisible until commit.
        sessions.insert(nextId, session.familyId(), userId, sha256(nextRefresh),
                nextClaims.expiresAt(), userAgent);
        if (sessions.consume(tokenId, nextId) != 1) {
            sessions.delete(nextId);
            sessions.revokeFamily(session.familyId());
            throw new RefreshRejectedException();
        }
        return new IssuedTokens(tokens.createAccessToken(principal), nextRefresh, principal,
                accessTtlSeconds, refreshTtlSeconds);
    }

    @Transactional
    public void logout(String serializedRefresh) {
        if (serializedRefresh == null || serializedRefresh.isBlank()) return;
        // A matching persisted hash proves this exact serialized token belongs to a session;
        // no unexpired access token (or even an unexpired refresh JWT) is required to log out.
        sessions.findForUpdate(sha256(serializedRefresh))
                .ifPresent(session -> sessions.revokeFamily(session.familyId()));
    }

    public UserPrincipal currentPrincipal(UUID userId) {
        return users.findPrincipal(userId).orElseThrow(AuthenticationException::new);
    }

    public record IssuedTokens(String accessToken, String refreshToken, UserPrincipal principal,
                               long accessTtlSeconds, long refreshTtlSeconds) {
    }
}
