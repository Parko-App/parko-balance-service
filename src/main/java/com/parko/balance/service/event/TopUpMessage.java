package com.parko.balance.service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TopUpMessage(
        UUID operationId,
        UUID userId,
        BigDecimal amount,
        Instant timestamp
) {
}
