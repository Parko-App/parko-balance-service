package com.parko.balance.service.service;

import com.parko.balance.service.cache.PreferenceCache;
import com.parko.balance.service.converter.TransactionConverter;
import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.dto.response.TopUpStatusResponse;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.exception.OwnershipMismatchException;
import com.parko.balance.service.exception.TopUpAmountExceededException;
import com.parko.balance.service.publisher.TopUpPublisher;
import com.parko.domain.lib.model.Transaction;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.model.entity.UserEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import com.parko.persistence.core.repository.UserRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class BalanceService {

    private static final String TOPUP_PAYMENT_PROVIDER = "MERCADO_PAGO";
    private static final String CHARGE_PAYMENT_PROVIDER = "INTERNAL_BALANCE";

    private final TopUpPublisher topUpPublisher;
    private final UserRepository userRepository;
    private final BalanceAccountRepository balanceAccountRepository;
    private final TransactionRepository transactionRepository;
    private final PreferenceCache preferenceCache;
    private final BigDecimal maxTopUpAmount;

    public BalanceService(TopUpPublisher topUpPublisher,
                           UserRepository userRepository,
                           BalanceAccountRepository balanceAccountRepository,
                           TransactionRepository transactionRepository,
                           PreferenceCache preferenceCache,
                           @Value("${balance.topup.max-amount:100000}") BigDecimal maxTopUpAmount) {
        this.topUpPublisher = topUpPublisher;
        this.userRepository = userRepository;
        this.balanceAccountRepository = balanceAccountRepository;
        this.transactionRepository = transactionRepository;
        this.preferenceCache = preferenceCache;
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

        BalanceAccountEntity account = balanceAccountRepository.findByUserId(request.userId())
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + request.userId()));

        UUID operationId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = Transaction.topUp(operationId, account.getId(), request.amount(),
                TransactionStatus.PENDING, TOPUP_PAYMENT_PROVIDER, null);
        TransactionEntity transactionEntity = com.parko.persistence.core.converters.TransactionConverter
                .toEntity(TransactionConverter.toEmbedded(transaction, now, now));
        transactionRepository.save(transactionEntity);

        TopUpMessage message = new TopUpMessage(operationId, request.userId(), request.amount(), Instant.now());
        try {
            topUpPublisher.publish(message);
        } catch (AmqpException e) {
            transactionEntity.setStatus(TransactionStatus.FAILED);
            transactionEntity.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transactionEntity);
            throw e;
        }
        return operationId;
    }

    public UUID charge(ChargeRequest request) {
        BalanceAccountEntity account = balanceAccountRepository.findByUserId(request.userId())
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + request.userId()));

        boolean alreadyNegative = account.getAmount().compareTo(BigDecimal.ZERO) < 0;
        TransactionStatus status = alreadyNegative ? TransactionStatus.PENDING : TransactionStatus.COMPLETED;

        if (!alreadyNegative) {
            account.setAmount(account.getAmount().subtract(request.amount()));
            account.setUpdatedAt(LocalDateTime.now());
            balanceAccountRepository.save(account);
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<TransactionEntity> pending = transactionRepository.findByParkingSession_IdAndTypeAndStatus(
                request.parkingSessionId(), TransactionType.CHARGE, TransactionStatus.PENDING);

        TransactionEntity transactionEntity;
        if (pending.isPresent()) {
            transactionEntity = pending.get();
            transactionEntity.setAmount(request.amount());
            transactionEntity.setStatus(status);
            transactionEntity.setUpdatedAt(now);
        } else {
            Transaction transaction = Transaction.charge(UUID.randomUUID(), account.getId(), request.parkingSessionId(),
                    request.amount(), status, CHARGE_PAYMENT_PROVIDER, null);
            transactionEntity = com.parko.persistence.core.converters.TransactionConverter
                    .toEntity(TransactionConverter.toEmbedded(transaction, now, now));
        }
        transactionRepository.save(transactionEntity);

        return transactionEntity.getId();
    }

    public Optional<String> findPreference(UUID operationId) {
        return preferenceCache.find(operationId);
    }

    public TopUpStatusResponse findTopUpStatus(UUID operationId) {
        TransactionEntity transaction = transactionRepository.findById(operationId)
                .filter(entity -> entity.getType() == TransactionType.TOPUP)
                .orElseThrow(() -> new NoSuchElementException("Recarga no encontrada para operationId: " + operationId));

        return new TopUpStatusResponse(operationId, transaction.getStatus());
    }
}
