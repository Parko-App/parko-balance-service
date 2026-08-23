package com.parko.balance.service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TopUpRequest(
        @NotNull UUID userId,
        @NotNull @Positive BigDecimal amount
) {
}
