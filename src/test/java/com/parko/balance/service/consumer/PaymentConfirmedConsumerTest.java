package com.parko.balance.service.consumer;

import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmedConsumerTest {

    @Mock
    private BalanceAccountRepository balanceAccountRepository;

    private PaymentConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentConfirmedConsumer(balanceAccountRepository);
    }

    @Test
    void onPaymentConfirmed_addsAmountToExistingBalance() {
        UUID userId = UUID.randomUUID();
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setAmount(BigDecimal.valueOf(100));
        account.setUpdatedAt(LocalDateTime.now().minusDays(1));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        PaymentConfirmedMessage message = new PaymentConfirmedMessage(UUID.randomUUID(), userId, BigDecimal.valueOf(50));
        consumer.onPaymentConfirmed(message);

        ArgumentCaptor<BalanceAccountEntity> captor = ArgumentCaptor.forClass(BalanceAccountEntity.class);
        verify(balanceAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    @Test
    void onPaymentConfirmed_throws_whenAccountNotFound() {
        UUID userId = UUID.randomUUID();
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        PaymentConfirmedMessage message = new PaymentConfirmedMessage(UUID.randomUUID(), userId, BigDecimal.TEN);

        assertThatThrownBy(() -> consumer.onPaymentConfirmed(message))
                .isInstanceOf(NoSuchElementException.class);
    }
}
