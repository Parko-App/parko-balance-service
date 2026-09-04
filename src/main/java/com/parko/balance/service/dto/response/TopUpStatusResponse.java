package com.parko.balance.service.dto.response;

import com.parko.domain.lib.model.TransactionStatus;

import java.util.UUID;

public record TopUpStatusResponse(UUID operationId, TransactionStatus status) {
}
