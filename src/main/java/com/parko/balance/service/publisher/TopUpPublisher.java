package com.parko.balance.service.publisher;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.TopUpMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TopUpPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TopUpPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(TopUpMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.BALANCE_EXCHANGE, RabbitConfig.TOPUP_ROUTING_KEY, message);
    }
}
