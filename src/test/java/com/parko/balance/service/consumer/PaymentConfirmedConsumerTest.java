package com.parko.balance.service.consumer;

import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmedConsumerTest {

    @Mock
    private BalanceAccountRepository balanceAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EntityManager entityManager;

    private PaymentConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentConfirmedConsumer(balanceAccountRepository, transactionRepository, entityManager);
    }

    private BalanceAccountEntity accountWithId(UUID id) {
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setId(id);
        account.setAmount(BigDecimal.valueOf(100));
        account.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return account;
    }

    private void transactionExists(UUID operationId, TransactionEntity transaction) {
        when(entityManager.find(TransactionEntity.class, operationId, LockModeType.PESSIMISTIC_WRITE)).thenReturn(transaction);
    }

    private TransactionEntity transaction(UUID id, UUID balanceAccountId, BigDecimal amount, TransactionStatus status) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        BalanceAccountEntity balanceAccount = new BalanceAccountEntity();
        balanceAccount.setId(balanceAccountId);
        transaction.setBalanceAccount(balanceAccount);
        transaction.setType(TransactionType.TOPUP);
        transaction.setAmount(amount);
        transaction.setStatus(status);
        transaction.setPaymentProvider("MERCADO_PAGO");
        transaction.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        transaction.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return transaction;
    }

    private TransactionEntity pendingTransaction(UUID id, UUID balanceAccountId, BigDecimal amount) {
        return transaction(id, balanceAccountId, amount, TransactionStatus.PENDING);
    }

    @Test
    void onPaymentConfirmed_addsAmountToExistingBalance() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(50)));

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(50)));

        ArgumentCaptor<BalanceAccountEntity> captor = ArgumentCaptor.forClass(BalanceAccountEntity.class);
        verify(balanceAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    @Test
    void onPaymentConfirmed_completesPendingTransaction() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(50)));

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(50)));

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(operationId);
        assertThat(saved.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(saved.getBalanceAccount().getId()).isEqualTo(balanceAccountId);
    }

    @Test
    void onPaymentConfirmed_marksTransactionFailed_whenAccountNotFound() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, UUID.randomUUID(), BigDecimal.TEN));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.TEN));

        verify(balanceAccountRepository, never()).save(any());
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void onPaymentConfirmed_throws_whenPendingTransactionNotFound() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, null);

        PaymentConfirmedMessage message = new PaymentConfirmedMessage(operationId, userId, BigDecimal.TEN);

        assertThatThrownBy(() -> consumer.onPaymentConfirmed(message))
                .isInstanceOf(NoSuchElementException.class);

        verify(balanceAccountRepository, never()).findByUserId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void onPaymentConfirmed_ignoresDuplicateMessage_whenAlreadyCompleted() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId,
                transaction(operationId, UUID.randomUUID(), BigDecimal.valueOf(50), TransactionStatus.COMPLETED));

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(50)));

        verify(balanceAccountRepository, never()).findByUserId(any());
        verify(balanceAccountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void onPaymentConfirmed_credits_whenAnEarlierAttemptWasRejected_soALegitimateApprovalIsNotLost() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId,
                transaction(operationId, balanceAccountId, BigDecimal.valueOf(50), TransactionStatus.FAILED));

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(50)));

        assertThat(account.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void onPaymentConfirmed_readsTheTransactionUnderRowLock() {
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, transaction(operationId, UUID.randomUUID(), BigDecimal.TEN, TransactionStatus.COMPLETED));

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, UUID.randomUUID(), BigDecimal.TEN));

        verify(entityManager).find(TransactionEntity.class, operationId, LockModeType.PESSIMISTIC_WRITE);
        verify(transactionRepository, never()).findById(any());
    }

    private TransactionEntity pendingCharge(UUID balanceAccountId, BigDecimal amount) {
        TransactionEntity charge = transaction(UUID.randomUUID(), balanceAccountId, amount, TransactionStatus.PENDING);
        charge.setType(TransactionType.CHARGE);
        charge.setPaymentProvider("INTERNAL_BALANCE");
        return charge;
    }

    private void pendingChargesExist(UUID balanceAccountId, TransactionEntity... charges) {
        when(transactionRepository.findByBalanceAccount_IdAndTypeAndStatusOrderByCreatedAtAsc(
                balanceAccountId, TransactionType.CHARGE, TransactionStatus.PENDING)).thenReturn(List.of(charges));
    }

    @Test
    void onPaymentConfirmed_settlesPendingCharge_withTheCreditedAmount() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(-500));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(2000)));
        TransactionEntity charge = pendingCharge(balanceAccountId, BigDecimal.valueOf(1000));
        pendingChargesExist(balanceAccountId, charge);

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(2000)));

        assertThat(account.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(charge.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(transactionRepository).save(charge);
        verify(balanceAccountRepository).save(account);
    }

    @Test
    void onPaymentConfirmed_settlesPendingCharge_evenIfBalanceEndsNegative() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(-500));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(600)));
        TransactionEntity charge = pendingCharge(balanceAccountId, BigDecimal.valueOf(1000));
        pendingChargesExist(balanceAccountId, charge);

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(600)));

        assertThat(account.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-900));
        assertThat(charge.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void onPaymentConfirmed_leavesPendingCharge_whenBalanceIsStillNegativeAfterCredit() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(-500));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(200)));
        TransactionEntity charge = pendingCharge(balanceAccountId, BigDecimal.valueOf(1000));
        pendingChargesExist(balanceAccountId, charge);

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(200)));

        assertThat(account.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-300));
        assertThat(charge.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(transactionRepository, never()).save(charge);
    }

    @Test
    void onPaymentConfirmed_settlesPendingChargesOldestFirst_untilBalanceGoesNegative() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(-500));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        UUID operationId = UUID.randomUUID();
        transactionExists(operationId, pendingTransaction(operationId, balanceAccountId, BigDecimal.valueOf(1000)));
        TransactionEntity older = pendingCharge(balanceAccountId, BigDecimal.valueOf(1000));
        TransactionEntity newer = pendingCharge(balanceAccountId, BigDecimal.valueOf(1000));
        pendingChargesExist(balanceAccountId, older, newer);

        consumer.onPaymentConfirmed(new PaymentConfirmedMessage(operationId, userId, BigDecimal.valueOf(1000)));

        assertThat(account.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-500));
        assertThat(older.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(newer.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void onPaymentConfirmed_isTransactional_soBalanceAndTransactionAreSavedTogether() throws NoSuchMethodException {
        assertThat(PaymentConfirmedConsumer.class.getMethod("onPaymentConfirmed", PaymentConfirmedMessage.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isTrue();
    }
}
