package com.parko.balance.service.consumer;

import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.TopUpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TopUpConsumer {

    private static final Logger log = LoggerFactory.getLogger(TopUpConsumer.class);

    @RabbitListener(queues = RabbitConfig.TOPUP_QUEUE)
    public void onTopUp(TopUpMessage message) {
        log.info("TopUpMessage recibido: {}", message);
    }
}
