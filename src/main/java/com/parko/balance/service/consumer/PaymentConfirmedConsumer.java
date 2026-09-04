package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import com.parko.persistence.core.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
public class PaymentConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmedConsumer.class);

    private final BalanceAccountRepository balanceAccountRepository;
    private final TransactionRepository transactionRepository;

    public PaymentConfirmedConsumer(BalanceAccountRepository balanceAccountRepository,
                                     TransactionRepository transactionRepository) {
        this.balanceAccountRepository = balanceAccountRepository;
        this.transactionRepository = transactionRepository;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_CONFIRMED_QUEUE)
    public void onPaymentConfirmed(PaymentConfirmedMessage message) {
        TransactionEntity transaction = transactionRepository.findById(message.operationId())
                .orElseThrow(() -> new NoSuchElementException("Transacción pendiente no encontrada para operationId: " + message.operationId()));

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
