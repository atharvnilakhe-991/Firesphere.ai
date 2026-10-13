package com.firesphere.backend.payment.controller;

import com.firesphere.backend.payment.dto.PaymentCallbackRequest;
import com.firesphere.backend.payment.service.PaymentCallbackAuthenticator;
import com.firesphere.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

@RestController
public class PaymentController {
    
    private final PaymentService paymentService;
    private final PaymentCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, PaymentCallbackAuthenticator authenticator, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/noc/applications/{applicationId}/payments")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('CITIZEN')")
    public ResponseEntity<Void> initiatePayment(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        paymentService.initiatePayment(applicationId, idempotencyKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/payments/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestHeader(value = "X-Provider-Signature", required = false) String signature,
            @Valid @RequestBody PaymentCallbackRequest request) {
        
        try {
            String payload = objectMapper.writeValueAsString(request);
            if (!authenticator.authenticate(payload, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        paymentService.processCallback(request);
        return ResponseEntity.ok().build();
    }
}
