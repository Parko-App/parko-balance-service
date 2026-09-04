package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.PaymentFailedMessage;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.persistence.core.model.entity.TransactionEntity;
import com.parko.persistence.core.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Component
public class PaymentFailedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    private final TransactionRepository transactionRepository;

    public PaymentFailedConsumer(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(PaymentFailedMessage message) {
        TransactionEntity transaction = transactionRepository.findById(message.operationId())
                .orElseThrow(() -> new NoSuchElementException("Transacción pendiente no encontrada para operationId: " + message.operationId()));

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("Transacción marcada como FAILED para operationId={}, reason={}", message.operationId(), message.reason());
    }
}
