package com.spry.library.config;

import com.spry.shared.security.JwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    /**
     * JwtAuthFilter is a Filter bean picked up by @WebMvcTest. It requires JwtService,
     * which is excluded from the web slice by type filtering. We supply a real instance
     * here so the filter can start; in tests it never receives a Bearer token so parse()
     * is never called.
     */
    @Bean
    public JwtService jwtService() {
        return new JwtService("test-secret-key-that-is-long-enough-for-hmac");
    }

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized"))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Forbidden")))
                .build();
    }
}
