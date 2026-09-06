package com.parko.balance.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class KeycloakAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public KeycloakAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    var jwt = jwtDecoder.decode(header.substring(7));
                    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
                } catch (JwtException e) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
