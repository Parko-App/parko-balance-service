package com.parko.balance.service.consumer;

import com.parko.balance.service.event.PaymentFailedMessage;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFailedConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EntityManager entityManager;

    private PaymentFailedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentFailedConsumer(transactionRepository, entityManager);
    }

    private TransactionEntity transaction(UUID id, TransactionStatus status) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        BalanceAccountEntity balanceAccount = new BalanceAccountEntity();
        balanceAccount.setId(UUID.randomUUID());
        transaction.setBalanceAccount(balanceAccount);
        transaction.setType(TransactionType.TOPUP);
        transaction.setAmount(BigDecimal.valueOf(50));
        transaction.setStatus(status);
        transaction.setPaymentProvider("MERCADO_PAGO");
        transaction.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        transaction.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return transaction;
    }

    private void transactionExists(UUID operationId, TransactionEntity transaction) {
        when(entityManager.find(TransactionEntity.class, operationId, LockModeType.PESSIMISTIC_WRITE)).thenReturn(transaction);
    }

    @Test
    void onPaymentFailed_marksPendingTransactionAsFailed() {
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, transaction(operationId, TransactionStatus.PENDING));

        consumer.onPaymentFailed(new PaymentFailedMessage(operationId, "rejected"));

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(operationId);
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void onPaymentFailed_throws_whenTransactionNotFound() {
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, null);

        PaymentFailedMessage message = new PaymentFailedMessage(operationId, "cancelled");

        assertThatThrownBy(() -> consumer.onPaymentFailed(message))
                .isInstanceOf(NoSuchElementException.class);

        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onPaymentFailed_doesNotOverwriteACompletedTransaction() {
        UUID operationId = UUID.randomUUID();
        TransactionEntity completed = transaction(operationId, TransactionStatus.COMPLETED);
        transactionExists(operationId, completed);

        consumer.onPaymentFailed(new PaymentFailedMessage(operationId, "rejected"));

        assertThat(completed.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onPaymentFailed_readsTheTransactionUnderRowLock() {
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, transaction(operationId, TransactionStatus.PENDING));

        consumer.onPaymentFailed(new PaymentFailedMessage(operationId, "rejected"));

        verify(entityManager).find(TransactionEntity.class, operationId, LockModeType.PESSIMISTIC_WRITE);
        verify(transactionRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onPaymentFailed_isTransactional() throws NoSuchMethodException {
        assertThat(PaymentFailedConsumer.class.getMethod("onPaymentFailed", PaymentFailedMessage.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }
}
