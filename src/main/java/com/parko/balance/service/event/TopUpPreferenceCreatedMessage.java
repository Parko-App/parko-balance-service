package com.parko.balance.service.event;

import java.util.UUID;

public record TopUpPreferenceCreatedMessage(
        UUID operationId,
        String preferenceId
) {
}
