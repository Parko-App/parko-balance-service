package com.parko.balance.service.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPreferenceCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisPreferenceCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new RedisPreferenceCache(redisTemplate, 15);
    }

    @Test
    void save_setsKeyWithTtl() {
        UUID operationId = UUID.randomUUID();

        cache.save(operationId, "pref-1");

        verify(valueOperations).set("balance:topup-preference:" + operationId, "pref-1", Duration.ofMinutes(15));
    }

    @Test
    void find_returnsValue_whenPresent() {
        UUID operationId = UUID.randomUUID();
        when(valueOperations.get("balance:topup-preference:" + operationId)).thenReturn("pref-1");

        Optional<String> result = cache.find(operationId);

        assertThat(result).contains("pref-1");
    }

    @Test
    void find_returnsEmpty_whenAbsent() {
        UUID operationId = UUID.randomUUID();
        when(valueOperations.get("balance:topup-preference:" + operationId)).thenReturn(null);

        Optional<String> result = cache.find(operationId);

        assertThat(result).isEmpty();
    }
}
