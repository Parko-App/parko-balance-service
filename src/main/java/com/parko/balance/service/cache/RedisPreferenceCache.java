package com.parko.balance.service.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisPreferenceCache implements PreferenceCache {

    private static final String KEY_PREFIX = "balance:topup-preference:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisPreferenceCache(StringRedisTemplate redisTemplate,
                                 @Value("${balance.topup.preference-cache-ttl-minutes:15}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public void save(UUID operationId, String preferenceId) {
        redisTemplate.opsForValue().set(key(operationId), preferenceId, ttl);
    }

    @Override
    public Optional<String> find(UUID operationId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(operationId)));
    }

    private String key(UUID operationId) {
        return KEY_PREFIX + operationId;
    }
}
