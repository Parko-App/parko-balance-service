package com.parko.balance.service.consumer;

import com.parko.balance.service.cache.PreferenceCache;
import com.parko.balance.service.config.RabbitConfig;
import com.parko.balance.service.event.TopUpPreferenceCreatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TopUpPreferenceCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TopUpPreferenceCreatedConsumer.class);

    private final PreferenceCache preferenceCache;

    public TopUpPreferenceCreatedConsumer(PreferenceCache preferenceCache) {
        this.preferenceCache = preferenceCache;
    }

    @RabbitListener(queues = RabbitConfig.TOPUP_PREFERENCE_CREATED_QUEUE)
    public void onTopUpPreferenceCreated(TopUpPreferenceCreatedMessage message) {
        preferenceCache.save(message.operationId(), message.preferenceId());
        log.info("Preferencia cacheada para operationId={}", message.operationId());
    }
}
