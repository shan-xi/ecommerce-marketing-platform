package com.example.marketingplatform.identity.infrastructure;

import com.example.marketingplatform.identity.domain.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService implements com.example.marketingplatform.identity.application.TokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final String audience;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtTokenService(
            @Value("${app.security.jwt-secret-base64}") String secretBase64,
            @Value("${app.security.issuer}") String issuer,
            @Value("${app.security.audience}") String audience,
            @Value("${app.security.access-token-ttl:PT15M}") java.time.Duration accessTtl,
            @Value("${app.security.refresh-token-ttl:P7D}") java.time.Duration refreshTtl) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secretBase64);
        if (keyBytes.length < 32) throw new IllegalArgumentException("JWT HS256 key must be at least 256 bits");
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        // Do not accept an expired access/refresh JWT during the default 60-second
        // timestamp-validation grace period; the API contract treats exp as strict.
        nimbusDecoder.setJwtValidator(new JwtTimestampValidator(
                java.time.Duration.ZERO));
        this.decoder = nimbusDecoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtlSeconds = accessTtl.toSeconds();
        this.refreshTtlSeconds = refreshTtl.toSeconds();
        if (this.accessTtlSeconds <= 0 || this.refreshTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT token TTLs must be positive");
        }
    }

    public String createAccessToken(UserPrincipal principal) {
        return create(principal, "access", accessTtlSeconds);
    }

    public String createRefreshToken(UserPrincipal principal) {
        return create(principal, "refresh", refreshTtlSeconds);
    }

    private String create(UserPrincipal principal, String use, long ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(principal.userId().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString()).claim("role", principal.role()).claim("token_use", use).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt decode(String serialized) {
        Jwt jwt = decoder.decode(serialized);
        if (!issuer.equals(jwt.getClaimAsString("iss"))
                || jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
            throw new JwtException("Invalid token issuer or audience");
        }
        return jwt;
    }

    @Override
    public ParsedToken parse(String serializedToken) {
        Jwt jwt = decode(serializedToken);
        return new ParsedToken(
                UUID.fromString(jwt.getId()), UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("token_use"), jwt.getExpiresAt());
    }

    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
