package com.parko.balance.service.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

public class AllowedClientAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String AUTHORIZED_PARTY_CLAIM = "azp";

    private final String allowedClientId;

    public AllowedClientAuthorizationManager(String allowedClientId) {
        this.allowedClientId = allowedClientId;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean granted = auth instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()
                && allowedClientId != null
                && allowedClientId.equals(jwtAuth.getToken().getClaimAsString(AUTHORIZED_PARTY_CLAIM));
        return new AuthorizationDecision(granted);
    }
}
