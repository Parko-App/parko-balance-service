package com.parko.balance.service.consumer;

import com.parko.balance.service.event.TopUpMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class TopUpConsumerTest {

    @Test
    void onTopUp_doesNotThrow() {
        TopUpConsumer consumer = new TopUpConsumer();
        TopUpMessage message = new TopUpMessage(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());

        assertThatCode(() -> consumer.onTopUp(message)).doesNotThrowAnyException();
    }
}
