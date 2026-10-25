package com.dineflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@Component
public class JwtService {
    private final String secret;
    private final long expirationMs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtService(
            @Value("${dineflow.jwt.secret:DineFlowSecureJwtSecretKeyForAuthentication2026!#RestPersistToken}") String secret,
            @Value("${dineflow.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role) {
        try {
            long now = System.currentTimeMillis();
            long exp = now + expirationMs;

            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            Map<String, Object> payloadMap = Map.of(
                    "sub", username,
                    "role", role.toUpperCase(),
                    "iat", now / 1000,
                    "exp", exp / 1000
            );
            String payloadJson = objectMapper.writeValueAsString(payloadMap);

            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String dataToSign = encodedHeader + "." + encodedPayload;
            String signature = sign(dataToSign);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }

    public TokenClaims validateAndParseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String signature = parts[2];

        String expectedSignature = sign(encodedHeader + "." + encodedPayload);
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
            Map<?, ?> map = objectMapper.readValue(payloadBytes, Map.class);

            Object expObj = map.get("exp");
            if (expObj instanceof Number expNumber) {
                long expSeconds = expNumber.longValue();
                if (System.currentTimeMillis() / 1000 > expSeconds) {
                    return null; // Expired
                }
            }

            String username = String.valueOf(map.get("sub"));
            String role = String.valueOf(map.get("role"));
            return new TokenClaims(username, role);
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    public record TokenClaims(String username, String role) {}
}

