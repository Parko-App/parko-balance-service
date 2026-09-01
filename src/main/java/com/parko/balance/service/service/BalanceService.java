package com.parko.balance.service.service;

import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.exception.OwnershipMismatchException;
import com.parko.balance.service.exception.TopUpAmountExceededException;
import com.parko.balance.service.publisher.TopUpPublisher;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BalanceService {

    private final TopUpPublisher topUpPublisher;
    private final UserRepository userRepository;
    private final BigDecimal maxTopUpAmount;

    public BalanceService(TopUpPublisher topUpPublisher,
                           UserRepository userRepository,
                           @Value("${balance.topup.max-amount:100000}") BigDecimal maxTopUpAmount) {
        this.topUpPublisher = topUpPublisher;
        this.userRepository = userRepository;
        this.maxTopUpAmount = maxTopUpAmount;
    }

    public UUID topUp(TopUpRequest request, String firebaseUid) {
        UserEntity user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado para firebaseUid: " + firebaseUid));

        if (!user.getId().equals(request.userId())) {
            throw new OwnershipMismatchException("El usuario autenticado no coincide con el userId del request");
        }

        if (request.amount().compareTo(maxTopUpAmount) > 0) {
            throw new TopUpAmountExceededException("El monto excede el máximo permitido de " + maxTopUpAmount);
        }

        UUID operationId = UUID.randomUUID();
        TopUpMessage message = new TopUpMessage(operationId, request.userId(), request.amount(), Instant.now());
        topUpPublisher.publish(message);
        return operationId;
    }

    public UUID charge(ChargeRequest request) {
        return UUID.randomUUID();
    }
}
