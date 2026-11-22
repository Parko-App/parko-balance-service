package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
public class PaymentConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmedConsumer.class);

    private final BalanceAccountRepository balanceAccountRepository;
    private final TransactionRepository transactionRepository;
    private final EntityManager entityManager;

    public PaymentConfirmedConsumer(BalanceAccountRepository balanceAccountRepository,
                                     TransactionRepository transactionRepository,
                                     EntityManager entityManager) {
        this.balanceAccountRepository = balanceAccountRepository;
        this.transactionRepository = transactionRepository;
        this.entityManager = entityManager;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_CONFIRMED_QUEUE)
    @Transactional
    public void onPaymentConfirmed(PaymentConfirmedMessage message) {
        TransactionEntity transaction = entityManager.find(
                TransactionEntity.class, message.operationId(), LockModeType.PESSIMISTIC_WRITE);
        if (transaction == null) {
            throw new NoSuchElementException("Transacción pendiente no encontrada para operationId: " + message.operationId());
        }
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            log.warn("La operación {} ya estaba acreditada (COMPLETED), se ignora el mensaje duplicado", message.operationId());
            return;
        }

        Optional<BalanceAccountEntity> account = balanceAccountRepository.findByUserId(message.userId());
        if (account.isEmpty()) {
            log.error("Cuenta de saldo no encontrada para userId={} al confirmar operationId={}, transacción marcada como FAILED",
                    message.userId(), message.operationId());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            return;
        }

        BalanceAccountEntity balanceAccount = account.get();
        balanceAccount.setAmount(balanceAccount.getAmount().add(message.amount()));
        settlePendingCharges(balanceAccount);
        balanceAccount.setUpdatedAt(LocalDateTime.now());
        balanceAccountRepository.save(balanceAccount);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("Saldo acreditado para operationId={}, userId={}, amount={}",
                message.operationId(), message.userId(), message.amount());
    }

    /**
     * Cobra los estacionamientos que quedaron PENDING por haber entrado con saldo negativo,
     * del más viejo al más nuevo, con la misma regla que BalanceService.charge: se cobra
     * mientras el saldo no sea negativo (puede quedar negativo una sola vez).
     */
    private void settlePendingCharges(BalanceAccountEntity balanceAccount) {
        List<TransactionEntity> pendingCharges = transactionRepository
                .findByBalanceAccount_IdAndTypeAndStatusOrderByCreatedAtAsc(
                        balanceAccount.getId(), TransactionType.CHARGE, TransactionStatus.PENDING);

        for (TransactionEntity charge : pendingCharges) {
            if (balanceAccount.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                break;
            }
            balanceAccount.setAmount(balanceAccount.getAmount().subtract(charge.getAmount()));
            charge.setStatus(TransactionStatus.COMPLETED);
            charge.setUpdatedAt(LocalDateTime.now());
            transactionRepository.save(charge);
            log.info("Estacionamiento pendiente saldado con la recarga: transactionId={}, amount={}",
                    charge.getId(), charge.getAmount());
        }
    }
}
