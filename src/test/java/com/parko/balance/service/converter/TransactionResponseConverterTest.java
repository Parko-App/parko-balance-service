package com.parko.balance.service.converter;

import com.parko.balance.service.dto.response.TransactionResponse;
import com.parko.domain.lib.model.TransactionStatus;
import com.parko.domain.lib.model.TransactionType;
import com.parko.persistence.core.model.entity.TransactionEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionResponseConverterTest {

    private TransactionEntity entityWithTypeAndCreatedAt(TransactionType type, LocalDateTime createdAt) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setType(type);
        entity.setAmount(BigDecimal.valueOf(1200));
        entity.setStatus(TransactionStatus.COMPLETED);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void toResponse_mapsTopUpToCargaTitle() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.TOPUP, LocalDateTime.of(2026, 9, 6, 14, 5));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.title()).isEqualTo("Carga de saldo");
        assertThat(response.type()).isEqualTo("carga");
    }

    @Test
    void toResponse_mapsChargeToIngresoTitle() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.CHARGE, LocalDateTime.of(2026, 9, 6, 14, 5));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.title()).isEqualTo("Estacionamiento");
        assertThat(response.type()).isEqualTo("ingreso");
    }

    @Test
    void toResponse_mapsRefundToReembolsoTitle() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.REFUND, LocalDateTime.of(2026, 9, 6, 14, 5));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.title()).isEqualTo("Reembolso");
        assertThat(response.type()).isEqualTo("reembolso");
    }

    @Test
    void toResponse_formatsDateAsDayMonthYearWithoutLeadingZeros() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.TOPUP, LocalDateTime.of(2026, 9, 6, 14, 5));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.date()).isEqualTo("6/9/2026");
    }

    @Test
    void toResponse_formatsTimeAs12HourWithAmPm() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.TOPUP, LocalDateTime.of(2026, 9, 6, 14, 5));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.time()).isEqualTo("02:05 PM");
    }

    @Test
    void toResponse_copiesIdAndAmount() {
        TransactionEntity entity = entityWithTypeAndCreatedAt(TransactionType.CHARGE, LocalDateTime.of(2026, 1, 1, 9, 0));

        TransactionResponse response = TransactionResponseConverter.toResponse(entity);

        assertThat(response.id()).isEqualTo(entity.getId().toString());
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(1200));
    }
}
