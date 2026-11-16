package com.parko.balance.service.cache;

import java.util.Optional;
import java.util.UUID;

public interface PreferenceCache {

    void save(UUID operationId, String preferenceId);

    Optional<String> find(UUID operationId);
}
