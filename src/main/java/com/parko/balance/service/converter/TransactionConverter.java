package com.parko.balance.service.converter;

import com.parko.domain.lib.model.Transaction;
import com.parko.persistence.core.model.embedded.TransactionEmbedded;

import java.time.LocalDateTime;

public final class TransactionConverter {

    private TransactionConverter() {
    }

    public static TransactionEmbedded toEmbedded(Transaction transaction, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new TransactionEmbedded(
                transaction.getId(),
                transaction.getBalanceAccountId(),
                transaction.getParkingSessionId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getPaymentProvider(),
                transaction.getExternalRef(),
                createdAt,
                updatedAt
        );
    }
}
