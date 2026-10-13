package com.firesphere.backend.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class PaymentCallbackAuthenticator {

    private final String webhookSecret;

    public PaymentCallbackAuthenticator(@Value("${payment.webhook.secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean authenticate(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false; // Fail safely in production if missing
        }
        if (signature == null || payload == null) {
            return false;
        }

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(webhookSecret.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(payload.getBytes("UTF-8"));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            return MessageDigest.isEqual(expectedSignature.getBytes(), signature.getBytes());
        } catch (Exception e) {
            return false; // Malformed or error
        }
    }
}
