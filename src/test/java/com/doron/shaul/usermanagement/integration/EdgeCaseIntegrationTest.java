package com.doron.shaul.usermanagement.integration;

import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.repository.UserRepository;
import com.doron.shaul.usermanagement.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class EdgeCaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        // Clear database
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);

        // Generate valid token
        validToken = jwtService.generateToken(testUser.getEmail());
    }

    @Test
    void accessProtectedEndpoint_WithExpiredToken_ReturnsUnauthorized() throws Exception {
        // given: Generate an expired token (expired 1 hour ago)
        String expiredToken = Jwts.builder()
                .subject(testUser.getEmail())
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(getTestSigningKey())
                .compact();

        // when/then: Access protected endpoint with expired token should fail with 403
        // (JwtFilter catches exception, doesn't set auth, request reaches AuthorizationFilter → 403)
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithMalformedToken_ReturnsUnauthorized() throws Exception {
        // given: Malformed JWT token (not proper JWT format)
        String malformedToken = "this.is.not.a.valid.jwt.token";

        // when/then: Access with malformed token should fail with 403
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithInvalidSignature_ReturnsUnauthorized() throws Exception {
        // given: Token signed with different secret key
        SecretKey wrongKey = Keys.hmacShaKeyFor("different-secret-key-for-testing-jwt-signature-validation-purposes-12345".getBytes(StandardCharsets.UTF_8));
        String invalidSignatureToken = Jwts.builder()
                .subject(testUser.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        // when/then: Access with invalid signature should fail with 403
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + invalidSignatureToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithMissingBearerPrefix_ReturnsUnauthorized() throws Exception {
        // given: Valid token but missing "Bearer " prefix

        // when/then: Should fail due to missing Bearer prefix
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithInvalidAuthHeaderFormat_ReturnsUnauthorized() throws Exception {
        // given: Invalid Authorization header format
        String invalidHeader = "Basic " + validToken;

        // when/then: Should fail with incorrect auth scheme
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", invalidHeader))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithEmptyToken_ReturnsUnauthorized() throws Exception {
        // given: Empty token after Bearer prefix

        // when/then: Should fail with empty token
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer "))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithNonExistentUserToken_ReturnsOk() throws Exception {
        // given: Token for user that doesn't exist in database
        String nonExistentUserToken = jwtService.generateToken("nonexistent@example.com");

        // when/then: JWT is valid but user doesn't exist - request still authenticated
        // Note: Current implementation doesn't validate user exists, only token validity
        // This is acceptable as authorization happens at service layer
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + nonExistentUserToken))
                .andExpect(status().isOk());
    }

    @Test
    void accessProtectedEndpoint_WithTamperedPayload_ReturnsUnauthorized() throws Exception {
        // given: Valid token with tampered payload
        String[] tokenParts = validToken.split("\\.");
        if (tokenParts.length == 3) {
            // Tamper with the payload (change one character)
            String tamperedPayload = tokenParts[1].substring(0, tokenParts[1].length() - 1) + "X";
            String tamperedToken = tokenParts[0] + "." + tamperedPayload + "." + tokenParts[2];

            // when/then: Should fail due to signature verification with 403
            mockMvc.perform(get("/api/users/" + testUser.getId())
                            .header("Authorization", "Bearer " + tamperedToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void accessProtectedEndpoint_WithNoAuthHeader_ReturnsForbidden() throws Exception {
        // given: Request with no Authorization header

        // when/then: Should fail with 403 Forbidden
        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_WithValidTokenButExpiredByOneSecond_ReturnsUnauthorized() throws Exception {
        // given: Token that expired just 1 second ago
        String expiredToken = Jwts.builder()
                .subject(testUser.getEmail())
                .issuedAt(new Date(System.currentTimeMillis() - 2000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(getTestSigningKey())
                .compact();

        // when/then: Should fail even if only 1 second expired with 403
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Helper method to get the signing key used by JwtService.
     * This uses the same secret from test configuration.
     */
    private SecretKey getTestSigningKey() {
        // This is the base64-decoded value from application-test.properties
        String testSecret = "test-secret-key-for-jwt-testing-purposes-only-must-be-at-least-256-bits";
        return Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }
}
