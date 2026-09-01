package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.converter.TransactionConverter;
import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.domain.lib.model.Transaction;
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
import java.util.UUID;

@Component
public class PaymentConfirmedConsumer {

    private static final String PAYMENT_PROVIDER = "MERCADO_PAGO";

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
        BalanceAccountEntity account = balanceAccountRepository.findByUserId(message.userId())
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + message.userId()));

        account.setAmount(account.getAmount().add(message.amount()));
        account.setUpdatedAt(LocalDateTime.now());
        balanceAccountRepository.save(account);

        LocalDateTime now = LocalDateTime.now();
        Transaction transaction = Transaction.topUp(UUID.randomUUID(), account.getId(), message.amount(),
                TransactionStatus.COMPLETED, PAYMENT_PROVIDER, message.operationId().toString());
        TransactionEntity transactionEntity = com.parko.persistence.core.converters.TransactionConverter
                .toEntity(TransactionConverter.toEmbedded(transaction, now, now));
        transactionRepository.save(transactionEntity);

        log.info("Saldo acreditado para operationId={}, userId={}, amount={}",
                message.operationId(), message.userId(), message.amount());
    }
}
