package com.example.marketingplatform.identity.infrastructure;

import com.example.marketingplatform.identity.application.AuthenticationException;
import com.example.marketingplatform.identity.application.IdentityService;
import com.example.marketingplatform.identity.application.TokenService;
import com.example.marketingplatform.identity.domain.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class AccessCookieAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final IdentityService identity;

    public AccessCookieAuthenticationFilter(TokenService tokens, IdentityService identity) {
        this.tokens = tokens;
        this.identity = identity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                    .filter(cookie -> "access_token".equals(cookie.getName()))
                    .findFirst()
                    .ifPresent(cookie -> authenticate(cookie));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Cookie cookie) {
        final UUID userId;
        try {
            TokenService.ParsedToken parsed = tokens.parse(cookie.getValue());
            if (!"access".equals(parsed.tokenUse())) return;
            userId = parsed.userId();
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            return;
        }

        final UserPrincipal principal;
        try {
            principal = identity.currentPrincipal(userId);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
