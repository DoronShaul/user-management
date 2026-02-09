package com.doron.shaul.usermanagement.config;

import com.doron.shaul.usermanagement.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @InjectMocks
    private SecurityConfig securityConfig;

    @Test
    void passwordEncoder_ReturnsBCryptPasswordEncoder() {
        // when
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        // then
        assertNotNull(encoder);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }

    @Test
    void passwordEncoder_UsesStrength12() {
        // given
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String testPassword = "TestPassword123!";

        // when
        String hashedPassword = encoder.encode(testPassword);

        // then
        assertNotNull(hashedPassword);
        assertTrue(hashedPassword.startsWith("$2a$12$")); // BCrypt format with strength 12
        assertTrue(encoder.matches(testPassword, hashedPassword));
    }

    @Test
    void authenticationManager_ReturnsAuthenticationManager() throws Exception {
        // given
        AuthenticationConfiguration config = mock(AuthenticationConfiguration.class);
        AuthenticationManager mockAuthManager = mock(AuthenticationManager.class);
        when(config.getAuthenticationManager()).thenReturn(mockAuthManager);

        // when
        AuthenticationManager authManager = securityConfig.authenticationManager(config);

        // then
        assertNotNull(authManager);
        assertEquals(mockAuthManager, authManager);
    }
}
