package com.dineflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {
    private JwtService jwtService;
    private final String secret = "TestSecretKeyForJwtSigningMustBeLongEnough1234567890!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 3600000); // 1 hour expiration
    }

    @Test
    void testGenerateAndValidateAdminToken() {
        String token = jwtService.generateToken("admin", "ADMIN");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        JwtService.TokenClaims claims = jwtService.validateAndParseToken(token);
        assertNotNull(claims);
        assertEquals("admin", claims.username());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    void testGenerateAndValidateStaffToken() {
        String token = jwtService.generateToken("staff", "STAFF");
        assertNotNull(token);

        JwtService.TokenClaims claims = jwtService.validateAndParseToken(token);
        assertNotNull(claims);
        assertEquals("staff", claims.username());
        assertEquals("STAFF", claims.role());
    }

    @Test
    void testTamperedTokenFails() {
        String token = jwtService.generateToken("staff", "STAFF");
        String[] parts = token.split("\\.");
        // Modify payload or signature
        String tampered = parts[0] + "." + parts[1] + "X." + parts[2];
        assertNull(jwtService.validateAndParseToken(tampered));
    }

    @Test
    void testExpiredTokenFails() throws InterruptedException {
        // Create service with 1ms expiration
        JwtService shortLivedService = new JwtService(secret, 1);
        String token = shortLivedService.generateToken("admin", "ADMIN");
        Thread.sleep(10); // Wait for token to expire

        assertNull(shortLivedService.validateAndParseToken(token));
    }

    @Test
    void testInvalidTokenStrings() {
        assertNull(jwtService.validateAndParseToken(null));
        assertNull(jwtService.validateAndParseToken(""));
        assertNull(jwtService.validateAndParseToken("invalid.token"));
        assertNull(jwtService.validateAndParseToken("not.a.valid.jwt.token"));
    }
}

