package com.parko.balance.service.service;

import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.publisher.TopUpPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private TopUpPublisher topUpPublisher;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(topUpPublisher);
    }

    @Test
    void topUp_publishesMessageWithRequestDataAndReturnsOperationId() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(50);
        TopUpRequest request = new TopUpRequest(userId, amount);

        UUID operationId = balanceService.topUp(request);

        assertThat(operationId).isNotNull();

        ArgumentCaptor<TopUpMessage> captor = ArgumentCaptor.forClass(TopUpMessage.class);
        verify(topUpPublisher).publish(captor.capture());
        assertThat(captor.getValue().operationId()).isEqualTo(operationId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(amount);
        assertThat(captor.getValue().timestamp()).isNotNull();
    }

    @Test
    void topUp_eachCallGeneratesDifferentOperationId() {
        TopUpRequest request = new TopUpRequest(UUID.randomUUID(), BigDecimal.ONE);

        UUID first = balanceService.topUp(request);
        UUID second = balanceService.topUp(request);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void charge_returnsNonNullOperationId() {
        ChargeRequest request = new ChargeRequest(UUID.randomUUID(), BigDecimal.TEN);

        UUID operationId = balanceService.charge(request);

        assertThat(operationId).isNotNull();
    }

    @Test
    void charge_eachCallGeneratesDifferentOperationId() {
        ChargeRequest request = new ChargeRequest(UUID.randomUUID(), BigDecimal.TEN);

        UUID first = balanceService.charge(request);
        UUID second = balanceService.charge(request);

        assertThat(first).isNotEqualTo(second);
    }
}
