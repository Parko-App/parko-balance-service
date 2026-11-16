package com.parko.balance.service.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.parko.balance.service.controller.BalanceController;
import com.parko.balance.service.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@Import(BalanceSecurityConfig.class)
class BalanceSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void balanceEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/balance/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPath_withoutToken_returns401_insteadOfBeingPublic() throws Exception {
        mockMvc.perform(get("/api/v1/ruta/que/no/existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequest_doesNotCreateHttpSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/balance/transactions"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static final String CHARGE_BODY =
            "{\"userId\":\"" + UUID.randomUUID() + "\",\"parkingSessionId\":\"" + UUID.randomUUID() + "\",\"amount\":1.00}";

    private void stubKeycloakToken(String token, String azp) throws Exception {
        // un token de Keycloak no es un token de Firebase: la verificacion de Firebase lo rechaza
        when(firebaseAuth.verifyIdToken(token)).thenThrow(mock(FirebaseAuthException.class));
        when(jwtDecoder.decode(token)).thenReturn(
                Jwt.withTokenValue(token).header("alg", "none").claim("sub", "svc").claim("azp", azp).build());
    }

    @Test
    void charge_withTheAccessServiceToken_isAccepted() throws Exception {
        stubKeycloakToken("svc", "access-service");
        when(balanceService.charge(any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/balance/charge").header("Authorization", "Bearer svc")
                        .contentType("application/json").content(CHARGE_BODY))
                .andExpect(status().isAccepted());
    }

    @Test
    void charge_withAnotherKeycloakClient_returns403() throws Exception {
        stubKeycloakToken("otro", "otro-client");

        mockMvc.perform(post("/api/v1/balance/charge").header("Authorization", "Bearer otro")
                        .contentType("application/json").content(CHARGE_BODY))
                .andExpect(status().isForbidden());

        verify(balanceService, never()).charge(any());
    }

    @Test
    void charge_withAFirebaseUserToken_returns403() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid-atacante");
        when(firebaseAuth.verifyIdToken("good")).thenReturn(token);

        mockMvc.perform(post("/api/v1/balance/charge").header("Authorization", "Bearer good")
                        .contentType("application/json").content(CHARGE_BODY))
                .andExpect(status().isForbidden());

        verify(balanceService, never()).charge(any());
    }

    @Test
    void charge_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/balance/charge").contentType("application/json").content(CHARGE_BODY))
                .andExpect(status().isUnauthorized());

        verify(balanceService, never()).charge(any());
    }

    @Test
    void authenticatedRequest_withValidFirebaseToken_isAllowedAndCreatesNoSession() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid-1");
        when(firebaseAuth.verifyIdToken("good")).thenReturn(token);
        when(balanceService.findTransactions(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        MvcResult result = mockMvc.perform(get("/api/v1/balance/transactions")
                        .header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
