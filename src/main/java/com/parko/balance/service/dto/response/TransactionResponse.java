package com.parko.balance.service.dto.response;

import java.math.BigDecimal;

public record TransactionResponse(
        String id,
        String title,
        BigDecimal amount,
        String date,
        String time,
        String type
) {
}
