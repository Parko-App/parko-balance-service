package com.parko.balance.service.consumer;

import com.parko.balance.service.cache.PreferenceCache;
import com.parko.balance.service.event.TopUpPreferenceCreatedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopUpPreferenceCreatedConsumerTest {

    @Mock
    private PreferenceCache preferenceCache;

    private TopUpPreferenceCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TopUpPreferenceCreatedConsumer(preferenceCache);
    }

    @Test
    void onTopUpPreferenceCreated_savesToCache() {
        UUID operationId = UUID.randomUUID();
        TopUpPreferenceCreatedMessage message = new TopUpPreferenceCreatedMessage(operationId, "pref-1");

        consumer.onTopUpPreferenceCreated(message);

        verify(preferenceCache).save(operationId, "pref-1");
    }
}
