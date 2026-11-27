package com.example.marketingplatform.config;

import com.example.marketingplatform.identity.application.IdentityService;
import com.example.marketingplatform.identity.application.TokenService;
import com.example.marketingplatform.identity.infrastructure.AccessCookieAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(@Value("${app.security.cookie-secure:true}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.path("/").secure(secure).sameSite("Lax"));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.security.frontend-origin}") String frontendOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrf,
                                            TokenService tokens, IdentityService identity) throws Exception {
        AccessCookieAuthenticationFilter accessFilter = new AccessCookieAuthenticationFilter(tokens, identity);
        http
                .cors(Customizer.withDefaults())
                .csrf(c -> c.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/livez", "/readyz").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/campaigns/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/campaigns/*/coupons/*/claims",
                                "/api/v1/campaigns/*/events").hasRole("MEMBER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/member/**").hasRole("MEMBER")
                        .anyRequest().authenticated())
                .addFilterBefore(accessFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Authentication required\",\"status\":401,\"code\":\"UNAUTHENTICATED\",\"detail\":\"A valid session is required.\",\"traceId\":\"" + java.util.UUID.randomUUID() + "\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            boolean csrfFailure = ex instanceof CsrfException;
                            response.setStatus(403);
                            response.setContentType("application/problem+json");
                            String code = csrfFailure ? "CSRF_INVALID" : "FORBIDDEN";
                            String title = csrfFailure ? "CSRF validation failed" : "Forbidden";
                            String detail = csrfFailure ? "A valid CSRF token is required." : "The request is not permitted.";
                            String body = """
                                    {"type":"about:blank","title":"%s","status":403,"code":"%s","detail":"%s","traceId":"%s"}
                                    """.formatted(title, code, detail, java.util.UUID.randomUUID());
                            response.getWriter().write(body);
                        }));
        return http.build();
    }
}
