package com.parko.balance.service.service;

import com.parko.balance.service.cache.PreferenceCache;
import com.parko.balance.service.converter.TransactionConverter;
import com.parko.balance.service.converter.TransactionResponseConverter;
import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.dto.response.TopUpStatusResponse;
import com.parko.balance.service.dto.response.TransactionResponse;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.exception.InvalidQueryParameterException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class BalanceService {

    private static final String TOPUP_PAYMENT_PROVIDER = "MERCADO_PAGO";
    private static final String CHARGE_PAYMENT_PROVIDER = "INTERNAL_BALANCE";
    private static final int MAX_PAGE_SIZE = 100;

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

    @Transactional
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

    @Transactional(readOnly = true)
    public Optional<String> findPreference(UUID operationId, String firebaseUid) {
        if (findOwnedTopUp(operationId, firebaseUid).isEmpty()) {
            return Optional.empty();
        }
        return preferenceCache.find(operationId);
    }

    @Transactional(readOnly = true)
    public TopUpStatusResponse findTopUpStatus(UUID operationId, String firebaseUid) {
        TransactionEntity transaction = findOwnedTopUp(operationId, firebaseUid)
                .orElseThrow(() -> new NoSuchElementException("Recarga no encontrada para operationId: " + operationId));

        return new TopUpStatusResponse(operationId, transaction.getStatus());
    }

    private Optional<TransactionEntity> findOwnedTopUp(UUID operationId, String firebaseUid) {
        if (firebaseUid == null) {
            return Optional.empty();
        }
        return transactionRepository.findById(operationId)
                .filter(entity -> entity.getType() == TransactionType.TOPUP)
                .filter(entity -> entity.getBalanceAccount() != null
                        && entity.getBalanceAccount().getUser() != null
                        && firebaseUid.equals(entity.getBalanceAccount().getUser().getFirebaseUid()));
    }

    public Page<TransactionResponse> findTransactions(String firebaseUid, Integer month, Integer year, int page, int size) {
        Page<TransactionEntity> entities;
        if (month != null && year != null) {
            if (month < 1 || month > 12) {
                throw new InvalidQueryParameterException("month debe estar entre 1 y 12");
            }
            if (year < 1 || year > 9999) {
                throw new InvalidQueryParameterException("year debe estar entre 1 y 9999");
            }
            LocalDateTime from = YearMonth.of(year, month).atDay(1).atStartOfDay();
            LocalDateTime to = from.plusMonths(1);
            int safePage = Math.max(page, 0);
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            entities = transactionRepository.findVisibleHistoryByFirebaseUidAndPeriod(
                    firebaseUid, from, to, TransactionType.TOPUP, TransactionStatus.COMPLETED,
                    PageRequest.of(safePage, safeSize));
        } else {
            Pageable lastThree = PageRequest.of(0, 3);
            entities = transactionRepository.findVisibleHistoryByFirebaseUid(
                    firebaseUid, TransactionType.TOPUP, TransactionStatus.COMPLETED, lastThree);
        }
        return entities.map(TransactionResponseConverter::toResponse);
    }
}
