package com.parko.balance.service.controller;

import tools.jackson.databind.ObjectMapper;
import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.dto.response.TopUpStatusResponse;
import com.parko.balance.service.service.BalanceService;
import com.parko.domain.lib.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void topUp_validRequest_returnsAccepted() throws Exception {
        TopUpRequest request = new TopUpRequest(UUID.randomUUID(), BigDecimal.TEN);
        UUID operationId = UUID.randomUUID();
        when(balanceService.topUp(any(TopUpRequest.class), any())).thenReturn(operationId);

        mockMvc.perform(post("/api/v1/balance/topup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("\"" + operationId + "\""));
    }

    @Test
    void topUp_missingUserId_returnsBadRequest() throws Exception {
        String invalidJson = "{\"amount\":10}";

        mockMvc.perform(post("/api/v1/balance/topup")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topUp_negativeAmount_returnsBadRequest() throws Exception {
        String invalidJson = "{\"userId\":\"" + UUID.randomUUID() + "\",\"amount\":-5}";

        mockMvc.perform(post("/api/v1/balance/topup")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void charge_validRequest_returnsAccepted() throws Exception {
        ChargeRequest request = new ChargeRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        UUID operationId = UUID.randomUUID();
        when(balanceService.charge(any(ChargeRequest.class))).thenReturn(operationId);

        mockMvc.perform(post("/api/v1/balance/charge")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("\"" + operationId + "\""));
    }

    @Test
    void charge_negativeAmount_returnsBadRequest() throws Exception {
        String invalidJson = "{\"userId\":\"" + UUID.randomUUID() + "\",\"amount\":-1}";

        mockMvc.perform(post("/api/v1/balance/charge")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPreference_returnsOk_whenPreferenceIsCached() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(balanceService.findPreference(operationId)).thenReturn(Optional.of("pref-1"));

        mockMvc.perform(get("/api/v1/balance/topup/" + operationId + "/preference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenceId").value("pref-1"));
    }

    @Test
    void getPreference_returnsNotFound_whenNotYetCached() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(balanceService.findPreference(operationId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/balance/topup/" + operationId + "/preference"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTopUpStatus_returnsOk_withStatusInBody() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(balanceService.findTopUpStatus(operationId))
                .thenReturn(new TopUpStatusResponse(operationId, TransactionStatus.FAILED));

        mockMvc.perform(get("/api/v1/balance/topup/" + operationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operationId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void getTopUpStatus_returnsNotFound_whenTransactionMissing() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(balanceService.findTopUpStatus(operationId))
                .thenThrow(new NoSuchElementException("Recarga no encontrada para operationId: " + operationId));

        mockMvc.perform(get("/api/v1/balance/topup/" + operationId))
                .andExpect(status().isNotFound());
    }
}
