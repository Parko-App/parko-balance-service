package com.parko.balance.service.publisher;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.TopUpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopUpPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TopUpPublisher topUpPublisher;

    @BeforeEach
    void setUp() {
        topUpPublisher = new TopUpPublisher(rabbitTemplate);
    }

    @Test
    void publish_sendsMessageToBalanceExchangeWithTopUpRoutingKey() {
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());

        topUpPublisher.publish(message);

        verify(rabbitTemplate).convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.TOPUP_ROUTING_KEY, message);
    }
}
