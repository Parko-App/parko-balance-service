package com.parko.balance.service.event;

import java.util.UUID;

public record PaymentFailedMessage(
        UUID operationId,
        String reason
) {
}
