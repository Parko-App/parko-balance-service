package com.parko.balance.service.service;

import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.exception.OwnershipMismatchException;
import com.parko.balance.service.exception.TopUpAmountExceededException;
import com.parko.balance.service.publisher.TopUpPublisher;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class BalanceServiceTest {

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(100000);
    private static final String FIREBASE_UID = "firebase-uid-123";

    @Mock
    private TopUpPublisher topUpPublisher;

    @Mock
    private UserRepository userRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(topUpPublisher, userRepository, MAX_AMOUNT);
    }

    private UserEntity userWithId(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirebaseUid(FIREBASE_UID);
        return user;
    }

    @Test
    void topUp_publishesMessageWithRequestDataAndReturnsOperationId() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(50);
        TopUpRequest request = new TopUpRequest(userId, amount);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));

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
    void topUp_eachCallGeneratesDifferentOperationId() {
        UUID userId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, BigDecimal.ONE);
        when(userRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(userWithId(userId)));

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
