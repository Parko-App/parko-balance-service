package com.parko.balance.service.controller;

import com.parko.balance.service.dto.request.ChargeRequest;
import com.parko.balance.service.dto.request.TopUpRequest;
import com.parko.balance.service.service.BalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/balance")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping("/topup")
    public ResponseEntity<UUID> topUp(@Valid @RequestBody TopUpRequest request, Authentication authentication) {
        String firebaseUid = authentication != null ? authentication.getName() : null;
        UUID operationId = balanceService.topUp(request, firebaseUid);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(operationId);
    }

    @PostMapping("/charge")
    public ResponseEntity<UUID> charge(@Valid @RequestBody ChargeRequest request, Authentication authentication) {
        UUID operationId = balanceService.charge(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(operationId);
    }

}
