package com.example.marketingplatform.identity.presentation;

import com.example.marketingplatform.identity.application.AuthenticationException;
import com.example.marketingplatform.identity.application.IdentityService;
import com.example.marketingplatform.identity.application.RefreshRejectedException;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identity;
    private final boolean secureCookies;

    public AuthController(IdentityService identity, @Value("${app.security.cookie-secure:true}") boolean secureCookies) {
        this.identity = identity;
        this.secureCookies = secureCookies;
    }

    @GetMapping("/csrf")
    public Map<String, Object> csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return Map.of("data", Map.of("csrfToken", csrfToken.getToken()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        if (request.email() == null || request.email().isBlank() || request.email().length() > 320
                || !request.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || request.password() == null || request.password().isBlank()
                || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new AuthenticationException();
        }
        var issued = identity.login(request.email(), request.password(), http.getHeader("User-Agent"));
        setAuthCookies(response, issued.accessToken(), issued.refreshToken(),
                issued.accessTtlSeconds(), issued.refreshTtlSeconds());
        return Map.of("data", Map.of("userId", issued.principal().userId(), "role", issued.principal().role()));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = cookie(request, "refresh_token");
        if (refresh == null) throw new RefreshRejectedException();
        var issued = identity.refresh(refresh, request.getHeader("User-Agent"));
        setAuthCookies(response, issued.accessToken(), issued.refreshToken(),
                issued.accessTtlSeconds(), issued.refreshTtlSeconds());
        return Map.of("data", Map.of("userId", issued.principal().userId(), "role", issued.principal().role()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        identity.logout(cookie(request, "refresh_token"));
        clearCookie(response, "access_token", "/api/v1", true);
        clearCookie(response, "refresh_token", "/api/v1/auth", true);
        clearCookie(response, "XSRF-TOKEN", "/", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("data", Map.of("userId", principal.userId(), "role", principal.role()));
    }

    private void setAuthCookies(HttpServletResponse response, String access, String refresh, long accessTtl, long refreshTtl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("access_token", access)
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1")
                .maxAge(accessTtl).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", refresh)
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/v1/auth")
                .maxAge(refreshTtl).build().toString());
    }

    private void clearCookie(HttpServletResponse response, String name, String path, boolean httpOnly) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "")
                .httpOnly(httpOnly).secure(secureCookies).sameSite("Lax").path(path).maxAge(0).build().toString());
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public record LoginRequest(String email, String password) {
    }
}
