package com.parko.balance.service.controller;

import tools.jackson.databind.ObjectMapper;
import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.dto.response.TopUpStatusResponse;
import com.parko.balance.service.dto.response.TransactionResponse;
import com.parko.balance.service.service.BalanceService;
import com.parko.domain.lib.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void getTransactions_returnsOk_withPageContent() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken("firebase-uid-123", null);
        TransactionResponse item = new TransactionResponse(
                UUID.randomUUID().toString(), "Carga de saldo", BigDecimal.valueOf(1200), "6/9/2026", "02:05 PM", "carga");
        Page<TransactionResponse> page = new PageImpl<>(List.of(item));
        when(balanceService.findTransactions(eq("firebase-uid-123"), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/balance/transactions").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Carga de saldo"))
                .andExpect(jsonPath("$.content[0].type").value("carga"));
    }

    @Test
    void getTransactions_passesMonthYearPageAndSizeFromQueryParams() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken("firebase-uid-123", null);
        when(balanceService.findTransactions(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/balance/transactions")
                        .principal(authentication)
                        .param("month", "9")
                        .param("year", "2026")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(balanceService).findTransactions("firebase-uid-123", 9, 2026, 2, 5);
    }

    @Test
    void getTransactions_defaultsPageAndSize_whenNotProvided() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken("firebase-uid-123", null);
        when(balanceService.findTransactions(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/balance/transactions").principal(authentication))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(balanceService).findTransactions("firebase-uid-123", null, null, 0, 20);
    }
}
