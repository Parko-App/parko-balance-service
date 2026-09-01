package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.PaymentConfirmedMessage;
import com.parko.persistence.core.model.entity.BalanceAccountEntity;
import com.parko.persistence.core.repository.BalanceAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Component
public class PaymentConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmedConsumer.class);

    private final BalanceAccountRepository balanceAccountRepository;

    public PaymentConfirmedConsumer(BalanceAccountRepository balanceAccountRepository) {
        this.balanceAccountRepository = balanceAccountRepository;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_CONFIRMED_QUEUE)
    public void onPaymentConfirmed(PaymentConfirmedMessage message) {
        BalanceAccountEntity account = balanceAccountRepository.findByUserId(message.userId())
                .orElseThrow(() -> new NoSuchElementException("Cuenta de saldo no encontrada para el usuario: " + message.userId()));

        account.setAmount(account.getAmount().add(message.amount()));
        account.setUpdatedAt(LocalDateTime.now());
        balanceAccountRepository.save(account);

        log.info("Saldo acreditado para operationId={}, userId={}, amount={}",
                message.operationId(), message.userId(), message.amount());
    }
}
