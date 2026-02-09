package com.doron.shaul.usermanagement.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_SECRET = "dGhpc2lzYXRlc3RzZWNyZXRrZXlmb3Jqd3R0b2tlbnNpdHNob3VsZGJlbG9uZ2Vub3VnaGZvcmhtYWNzaGEyNTY=";
    private static final long TEST_EXPIRATION = 900000; // 15 minutes

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", TEST_EXPIRATION);
    }

    @Test
    void generateToken_ValidEmail_ReturnsValidToken() {
        // given
        String email = TEST_EMAIL;

        // when
        String token = jwtService.generateToken(email);

        // then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    void extractEmail_ValidToken_ReturnsEmail() {
        // given
        String token = jwtService.generateToken(TEST_EMAIL);

        // when
        String extractedEmail = jwtService.extractEmail(token);

        // then
        assertEquals(TEST_EMAIL, extractedEmail);
    }

    @Test
    void isTokenValid_ValidToken_ReturnsTrue() {
        // given
        String token = jwtService.generateToken(TEST_EMAIL);

        // when
        boolean isValid = jwtService.isTokenValid(token);

        // then
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        // given
        String expiredToken = createExpiredToken(TEST_EMAIL);

        // when
        boolean isValid = jwtService.isTokenValid(expiredToken);

        // then
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_MalformedToken_ReturnsFalse() {
        // given
        String malformedToken = "this.is.not.a.valid.jwt.token";

        // when
        boolean isValid = jwtService.isTokenValid(malformedToken);

        // then
        assertFalse(isValid);
    }

    @Test
    void isTokenValid_WrongSignature_ReturnsFalse() {
        // given
        String tokenWithWrongSignature = createTokenWithWrongSignature(TEST_EMAIL);

        // when
        boolean isValid = jwtService.isTokenValid(tokenWithWrongSignature);

        // then
        assertFalse(isValid);
    }

    @Test
    void extractClaim_ValidToken_ReturnsSpecificClaim() {
        // given
        String token = jwtService.generateToken(TEST_EMAIL);

        // when
        Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);

        // then
        assertNotNull(issuedAt);
        assertTrue(issuedAt.before(new Date()));
    }

    @Test
    void extractAllClaims_ValidToken_ReturnsAllClaims() {
        // given
        String token = jwtService.generateToken(TEST_EMAIL);

        // when
        Claims claims = jwtService.extractAllClaims(token);

        // then
        assertNotNull(claims);
        assertEquals(TEST_EMAIL, claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    // Helper method to create an expired token
    private String createExpiredToken(String email) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis() - 1000000))
                .expiration(new Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
                .signWith(key)
                .compact();
    }

    // Helper method to create a token with wrong signature
    private String createTokenWithWrongSignature(String email) {
        String differentSecret = "ZGlmZmVyZW50c2VjcmV0a2V5Zm9yd3JvbmdzaWduYXR1cmV0ZXN0aW5naGVyZWl0c2hvdWxkYmVsb25nZW5vdWdoZm9yaG1hY3NoYTI1Ng==";
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentSecret));
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION))
                .signWith(wrongKey)
                .compact();
    }
}
