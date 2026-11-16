package com.parko.balance.service.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Autoriza solo a quien se autentico con un token de Keycloak emitido para un client concreto
 * (claim {@code azp}, "authorized party"). Se usa para endpoints que solo debe llamar otro servicio de
 * Parko (p. ej. {@code POST /balance/charge}, que invoca access-service con su token de service account) y
 * nunca un usuario final: un token de usuario de Firebase, o de cualquier otro client, queda rechazado.
 */
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
