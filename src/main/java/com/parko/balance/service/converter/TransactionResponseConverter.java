package com.parko.balance.service.converter;

import com.parko.balance.service.dto.response.TransactionResponse;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TransactionEntity;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TransactionResponseConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);

    private TransactionResponseConverter() {
    }

    public static TransactionResponse toResponse(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId().toString(),
                title(entity.getType()),
                entity.getAmount(),
                entity.getCreatedAt().format(DATE_FORMATTER),
                entity.getCreatedAt().format(TIME_FORMATTER),
                type(entity.getType())
        );
    }

    private static String title(TransactionType type) {
        return switch (type) {
            case TOPUP -> "Carga de saldo";
            case CHARGE -> "Estacionamiento";
            case REFUND -> "Reembolso";
        };
    }

    private static String type(TransactionType type) {
        return switch (type) {
            case TOPUP -> "carga";
            case CHARGE -> "ingreso";
            case REFUND -> "reembolso";
        };
    }
}
