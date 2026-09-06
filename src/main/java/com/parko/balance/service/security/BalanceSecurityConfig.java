package com.parko.balance.service.security;

import com.google.firebase.auth.FirebaseAuth;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class BalanceSecurityConfig {

    private final FirebaseAuth firebaseAuth;
    private final JwtDecoder jwtDecoder;

    public BalanceSecurityConfig(FirebaseAuth firebaseAuth, JwtDecoder jwtDecoder) {
        this.firebaseAuth = firebaseAuth;
        this.jwtDecoder = jwtDecoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        FirebaseAuthenticationFilter firebaseFilter = new FirebaseAuthenticationFilter(firebaseAuth);
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/balance/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new KeycloakAuthenticationFilter(jwtDecoder), FirebaseAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .build();
    }
}
