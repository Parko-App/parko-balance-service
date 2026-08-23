package com.parko.balance.service.service;

import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.event.TopUpMessage;
import com.parko.balance.service.publisher.TopUpPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BalanceService {

    private final TopUpPublisher topUpPublisher;

    public BalanceService(TopUpPublisher topUpPublisher) {
        this.topUpPublisher = topUpPublisher;
    }

    public UUID topUp(TopUpRequest request) {
        UUID operationId = UUID.randomUUID();
        TopUpMessage message = new TopUpMessage(operationId, request.userId(), request.amount(), Instant.now());
        topUpPublisher.publish(message);
        return operationId;
    }
}
