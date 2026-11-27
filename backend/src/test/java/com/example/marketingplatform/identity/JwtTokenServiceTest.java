package com.example.marketingplatform.identity;

import com.example.marketingplatform.identity.domain.UserPrincipal;
import com.example.marketingplatform.identity.infrastructure.JwtTokenService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private final JwtTokenService tokens = new JwtTokenService(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            "issuer-test", "audience-test", Duration.ofMinutes(15), Duration.ofDays(7));

    @Test
    void createsShortLivedAccessTokenWithMinimalIdentityClaims() {
        var principal = new UserPrincipal(UUID.randomUUID(), "ADMIN");
        var jwt = tokens.decode(tokens.createAccessToken(principal));
        assertThat(jwt.getSubject()).isEqualTo(principal.userId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("issuer-test");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getAudience()).containsExactly("audience-test");
    }

    @Test
    void refreshTokenHasRefreshPurposeAndSevenDayDefaultConfiguredByCaller() {
        var jwt = tokens.decode(tokens.createRefreshToken(new UserPrincipal(UUID.randomUUID(), "MEMBER")));
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("refresh");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsExpiredAccessToken() throws Exception {
        JwtTokenService shortLived = new JwtTokenService(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                "issuer-test", "audience-test", Duration.ofSeconds(1), Duration.ofDays(7));
        String token = shortLived.createAccessToken(new UserPrincipal(UUID.randomUUID(), "ADMIN"));

        Thread.sleep(1_100);

        assertThatThrownBy(() -> shortLived.decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    @Test
    void rejectsTokenWithTamperedSignature() {
        String valid = tokens.createAccessToken(new UserPrincipal(UUID.randomUUID(), "ADMIN"));
        int signatureStart = valid.lastIndexOf('.') + 1;
        char firstSignatureChar = valid.charAt(signatureStart);
        String tampered = valid.substring(0, signatureStart)
                + (firstSignatureChar == 'A' ? 'B' : 'A')
                + valid.substring(signatureStart + 1);

        assertThatThrownBy(() -> tokens.decode(tampered))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
