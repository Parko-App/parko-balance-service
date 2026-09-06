package com.parko.balance.service.service;

import com.parko.balance.service.cache.PreferenceCache;
import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.dto.response.TransactionResponse;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.exception.OwnershipMismatchException;
import com.parko.balance.service.exception.TopUpAmountExceededException;
import com.parko.balance.service.publisher.TopUpPublisher;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import com.parko.persistence.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(100000);
    private static final String FIREBASE_UID = "firebase-uid-123";

    @Mock
    private TopUpPublisher topUpPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceAccountRepository balanceAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PreferenceCache preferenceCache;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(topUpPublisher, userRepository, balanceAccountRepository,
                transactionRepository, preferenceCache, MAX_AMOUNT);
    }

    private UserEntity userWithId(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirebaseUid(FIREBASE_UID);
        return user;
    }

    private BalanceAccountEntity accountWithId(UUID id) {
        BalanceAccountEntity account = new BalanceAccountEntity();
        account.setId(id);
        account.setAmount(BigDecimal.ZERO);
        return account;
    }

    @Test
    void topUp_publishesMessageWithRequestDataAndReturnsOperationId() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(50);
        TopUpRequest request = new TopUpRequest(userId, amount);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(UUID.randomUUID())));

        UUID operationId = balanceService.topUp(request, FIREBASE_UID);

        assertThat(operationId).isNotNull();

        ArgumentCaptor<TopUpMessage> captor = ArgumentCaptor.forClass(TopUpMessage.class);
        verify(topUpPublisher).publish(captor.capture());
        assertThat(captor.getValue().operationId()).isEqualTo(operationId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(amount);
        assertThat(captor.getValue().timestamp()).isNotNull();
    }

    @Test
    void topUp_savesPendingTransactionBeforePublishing() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(50);
        TopUpRequest request = new TopUpRequest(userId, amount);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(balanceAccountId)));

        UUID operationId = balanceService.topUp(request, FIREBASE_UID);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        TransactionEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(operationId);
        assertThat(saved.getBalanceAccount().getId()).isEqualTo(balanceAccountId);
        assertThat(saved.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    void topUp_marksTransactionFailed_whenPublishFails() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, BigDecimal.valueOf(50));
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(balanceAccountId)));
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(topUpPublisher).publish(any());

        assertThatThrownBy(() -> balanceService.topUp(request, FIREBASE_UID))
                .isInstanceOf(org.springframework.amqp.AmqpException.class);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        TransactionEntity lastSaved = captor.getAllValues().get(1);
        assertThat(lastSaved.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void topUp_throwsNoSuchElement_whenBalanceAccountNotFound() {
        UUID userId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, BigDecimal.TEN);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.topUp(request, FIREBASE_UID))
                .isInstanceOf(NoSuchElementException.class);

        verify(transactionRepository, never()).save(any());
        verify(topUpPublisher, never()).publish(any());
    }

    @Test
    void topUp_eachCallGeneratesDifferentOperationId() {
        UUID userId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, BigDecimal.ONE);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(UUID.randomUUID())));

        UUID first = balanceService.topUp(request, FIREBASE_UID);
        UUID second = balanceService.topUp(request, FIREBASE_UID);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void topUp_throwsNoSuchElement_whenUserNotFound() {
        TopUpRequest request = new TopUpRequest(UUID.randomUUID(), BigDecimal.TEN);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.topUp(request, FIREBASE_UID))
                .isInstanceOf(NoSuchElementException.class);

        verify(topUpPublisher, never()).publish(any());
    }

    @Test
    void topUp_throwsOwnershipMismatch_whenAuthenticatedUserDiffersFromRequestUserId() {
        TopUpRequest request = new TopUpRequest(UUID.randomUUID(), BigDecimal.TEN);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(UUID.randomUUID())));

        assertThatThrownBy(() -> balanceService.topUp(request, FIREBASE_UID))
                .isInstanceOf(OwnershipMismatchException.class);

        verify(topUpPublisher, never()).publish(any());
    }

    @Test
    void topUp_throwsAmountExceeded_whenAmountAboveMax() {
        UUID userId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, MAX_AMOUNT.add(BigDecimal.ONE));
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));

        assertThatThrownBy(() -> balanceService.topUp(request, FIREBASE_UID))
                .isInstanceOf(TopUpAmountExceededException.class);

        verify(topUpPublisher, never()).publish(any());
    }

    @Test
    void charge_returnsNonNullOperationId() {
        UUID userId = UUID.randomUUID();
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(UUID.randomUUID())));
        ChargeRequest request = new ChargeRequest(userId, UUID.randomUUID(), BigDecimal.TEN);

        UUID operationId = balanceService.charge(request);

        assertThat(operationId).isNotNull();
    }

    @Test
    void charge_eachCallGeneratesDifferentOperationId() {
        UUID userId = UUID.randomUUID();
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(accountWithId(UUID.randomUUID())));
        ChargeRequest request = new ChargeRequest(userId, UUID.randomUUID(), BigDecimal.TEN);

        UUID first = balanceService.charge(request);
        UUID second = balanceService.charge(request);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void charge_debitsBalanceAndCompletesTransaction_whenEnoughBalance() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        UUID parkingSessionId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(100));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        ChargeRequest request = new ChargeRequest(userId, parkingSessionId, BigDecimal.valueOf(30));

        UUID operationId = balanceService.charge(request);

        ArgumentCaptor<BalanceAccountEntity> accountCaptor = ArgumentCaptor.forClass(BalanceAccountEntity.class);
        verify(balanceAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(70));

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        TransactionEntity saved = transactionCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(operationId);
        assertThat(saved.getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getParkingSession().getId()).isEqualTo(parkingSessionId);
        assertThat(saved.getBalanceAccount().getId()).isEqualTo(balanceAccountId);
    }

    @Test
    void charge_debitsIntoNegativeAndCompletesTransaction_whenBalanceInsufficientButNotYetNegative() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(10));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        ChargeRequest request = new ChargeRequest(userId, UUID.randomUUID(), BigDecimal.valueOf(30));

        balanceService.charge(request);

        ArgumentCaptor<BalanceAccountEntity> accountCaptor = ArgumentCaptor.forClass(BalanceAccountEntity.class);
        verify(balanceAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-20));

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void charge_leavesTransactionPendingWithoutDebiting_whenBalanceAlreadyNegative() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(-20));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        ChargeRequest request = new ChargeRequest(userId, UUID.randomUUID(), BigDecimal.valueOf(30));

        balanceService.charge(request);

        verify(balanceAccountRepository, never()).save(any());

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void charge_reusesExistingPendingTransaction_insteadOfCreatingANewOne() {
        UUID userId = UUID.randomUUID();
        UUID balanceAccountId = UUID.randomUUID();
        UUID parkingSessionId = UUID.randomUUID();
        UUID existingOperationId = UUID.randomUUID();
        BalanceAccountEntity account = accountWithId(balanceAccountId);
        account.setAmount(BigDecimal.valueOf(100));
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        TransactionEntity pendingEntity = new TransactionEntity();
        pendingEntity.setId(existingOperationId);
        pendingEntity.setType(TransactionType.CHARGE);
        pendingEntity.setStatus(TransactionStatus.PENDING);
        when(transactionRepository.findByParkingSession_IdAndTypeAndStatus(
                parkingSessionId, TransactionType.CHARGE, TransactionStatus.PENDING))
                .thenReturn(Optional.of(pendingEntity));

        ChargeRequest request = new ChargeRequest(userId, parkingSessionId, BigDecimal.valueOf(30));

        UUID operationId = balanceService.charge(request);

        assertThat(operationId).isEqualTo(existingOperationId);
        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getId()).isEqualTo(existingOperationId);
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void charge_throwsNoSuchElement_whenBalanceAccountNotFound() {
        UUID userId = UUID.randomUUID();
        when(balanceAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        ChargeRequest request = new ChargeRequest(userId, UUID.randomUUID(), BigDecimal.TEN);

        assertThatThrownBy(() -> balanceService.charge(request))
                .isInstanceOf(NoSuchElementException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void findPreference_returnsValue_whenPresentInCache() {
        UUID operationId = UUID.randomUUID();
        when(preferenceCache.find(operationId)).thenReturn(Optional.of("pref-1"));

        Optional<String> result = balanceService.findPreference(operationId);

        assertThat(result).contains("pref-1");
    }

    @Test
    void findPreference_returnsEmpty_whenNotYetInCache() {
        UUID operationId = UUID.randomUUID();
        when(preferenceCache.find(operationId)).thenReturn(Optional.empty());

        Optional<String> result = balanceService.findPreference(operationId);

        assertThat(result).isEmpty();
    }

    private TransactionEntity transactionWithTypeAndStatus(UUID id, TransactionType type, TransactionStatus status) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setAmount(BigDecimal.TEN);
        return transaction;
    }

    @Test
    void findTopUpStatus_returnsStatus_whenTopUpTransactionExists() {
        UUID operationId = UUID.randomUUID();
        when(transactionRepository.findById(operationId))
                .thenReturn(Optional.of(transactionWithTypeAndStatus(operationId, TransactionType.TOPUP, TransactionStatus.FAILED)));

        var result = balanceService.findTopUpStatus(operationId);

        assertThat(result.operationId()).isEqualTo(operationId);
        assertThat(result.status()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void findTopUpStatus_throwsNoSuchElement_whenTransactionNotFound() {
        UUID operationId = UUID.randomUUID();
        when(transactionRepository.findById(operationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.findTopUpStatus(operationId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findTopUpStatus_throwsNoSuchElement_whenTransactionIsNotATopUp() {
        UUID operationId = UUID.randomUUID();
        when(transactionRepository.findById(operationId))
                .thenReturn(Optional.of(transactionWithTypeAndStatus(operationId, TransactionType.CHARGE, TransactionStatus.COMPLETED)));

        assertThatThrownBy(() -> balanceService.findTopUpStatus(operationId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findTransactions_withMonthAndYear_queriesDateRangeForThatMonth() {
        String firebaseUid = "firebase-uid-abc";
        TransactionEntity entity = transactionWithTypeAndStatus(UUID.randomUUID(), TransactionType.TOPUP, TransactionStatus.COMPLETED);
        entity.setCreatedAt(LocalDateTime.of(2026, 9, 15, 10, 0));
        Page<TransactionEntity> page = new PageImpl<>(List.of(entity));
        when(transactionRepository.findByBalanceAccount_User_FirebaseUidAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(firebaseUid), any(), any(), any(Pageable.class))).thenReturn(page);

        Page<TransactionResponse> result = balanceService.findTransactions(firebaseUid, 9, 2026, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Carga de saldo");

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByBalanceAccount_User_FirebaseUidAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(firebaseUid), fromCaptor.capture(), toCaptor.capture(), pageableCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(toCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    void findTransactions_withoutMonthOrYear_returnsLastThreeIgnoringPageAndSize() {
        String firebaseUid = "firebase-uid-abc";
        when(transactionRepository.findByBalanceAccount_User_FirebaseUidOrderByCreatedAtDesc(eq(firebaseUid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        balanceService.findTransactions(firebaseUid, null, null, 3, 50);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByBalanceAccount_User_FirebaseUidOrderByCreatedAtDesc(eq(firebaseUid), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 3));
        verify(transactionRepository, never())
                .findByBalanceAccount_User_FirebaseUidAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any(), any());
    }

    @Test
    void findTransactions_withOnlyMonth_treatsAsMissingFilter() {
        String firebaseUid = "firebase-uid-abc";
        when(transactionRepository.findByBalanceAccount_User_FirebaseUidOrderByCreatedAtDesc(eq(firebaseUid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        balanceService.findTransactions(firebaseUid, 9, null, 0, 20);

        verify(transactionRepository).findByBalanceAccount_User_FirebaseUidOrderByCreatedAtDesc(eq(firebaseUid), any(Pageable.class));
        verify(transactionRepository, never())
                .findByBalanceAccount_User_FirebaseUidAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any(), any());
    }
}
