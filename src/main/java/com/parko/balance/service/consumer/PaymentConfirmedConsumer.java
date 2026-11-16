package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.domain.lib.model.TransactionStatus;
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

import java.time.LocalDateTime;
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

    /**
     * Acredita el saldo una sola vez por operacion. Los mensajes se entregan "al menos una vez" (Mercado Pago
     * reenvia webhooks y RabbitMQ reentrega si el consumer falla o tarda), asi que el mismo mensaje puede llegar
     * varias veces: este metodo es idempotente.
     * <ul>
     *   <li>Toda la operacion (saldo + transaccion) va en una unica transaccion: si algo falla, no queda nada a medias.</li>
     *   <li>La transaccion se lee con bloqueo de fila ({@code SELECT ... FOR UPDATE}): serializa entregas concurrentes y
     *       lee el estado ya confirmado, no uno viejo.</li>
     *   <li>Si ya esta COMPLETED se ignora. Se acredita tambien desde FAILED a proposito: una misma preferencia
     *       admite varios intentos de pago y uno rechazado puede ser seguido por uno aprobado.</li>
     * </ul>
     */
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
        balanceAccount.setUpdatedAt(LocalDateTime.now());
        balanceAccountRepository.save(balanceAccount);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("Saldo acreditado para operationId={}, userId={}, amount={}",
                message.operationId(), message.userId(), message.amount());
    }
}
