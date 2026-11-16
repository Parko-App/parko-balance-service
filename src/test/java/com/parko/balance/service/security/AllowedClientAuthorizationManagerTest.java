package com.parko.balance.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedClientAuthorizationManagerTest {

    private final AllowedClientAuthorizationManager manager = new AllowedClientAuthorizationManager("access-service");

    private boolean granted(Authentication authentication) {
        return manager.authorize(() -> authentication, null).isGranted();
    }

    private JwtAuthenticationToken tokenWithClaims(String azp) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "svc");
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return new JwtAuthenticationToken(builder.build(), Collections.emptyList());
    }

    @Test
    void grantsTheAllowedClient() {
        assertThat(granted(tokenWithClaims("access-service"))).isTrue();
    }

    @Test
    void deniesAnotherKeycloakClient() {
        assertThat(granted(tokenWithClaims("otro-client"))).isFalse();
    }

    @Test
    void deniesATokenWithoutAuthorizedPartyClaim() {
        assertThat(granted(tokenWithClaims(null))).isFalse();
    }

    @Test
    void deniesAFirebaseUser() {
        Authentication firebaseUser = new UsernamePasswordAuthenticationToken("firebase-uid", null, Collections.emptyList());

        assertThat(granted(firebaseUser)).isFalse();
    }

    @Test
    void deniesAnonymousAndMissingAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(granted(anonymous)).isFalse();
        assertThat(granted(null)).isFalse();
    }

    @Test
    void deniesEverything_whenNoClientIsConfigured() {
        AllowedClientAuthorizationManager unconfigured = new AllowedClientAuthorizationManager(null);

        assertThat(unconfigured.authorize(() -> tokenWithClaims("access-service"), null).isGranted()).isFalse();
    }
}
