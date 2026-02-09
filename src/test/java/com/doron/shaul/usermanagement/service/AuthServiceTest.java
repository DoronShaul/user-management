package com.doron.shaul.usermanagement.service;

import com.doron.shaul.usermanagement.dto.AuthResponse;
import com.doron.shaul.usermanagement.dto.LoginRequest;
import com.doron.shaul.usermanagement.dto.RegisterRequest;
import com.doron.shaul.usermanagement.model.Role;
import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.repository.*;
import com.doron.shaul.usermanagement.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthAuditLogRepository authAuditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 900000L); // 15 minutes
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L); // 7 days

        // Set up valid register request
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setName("John Doe");
        validRegisterRequest.setEmail("john@example.com");
        validRegisterRequest.setPassword("SecurePass123!");
        validRegisterRequest.setConfirmPassword("SecurePass123!");

        // Set up valid login request
        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("john@example.com");
        validLoginRequest.setPassword("SecurePass123!");

        // Set up test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("John Doe");
        testUser.setEmail("john@example.com");
        testUser.setPasswordHash("$2a$12$hashedPassword");
        testUser.setAccountEnabled(true);
        testUser.setAccountLocked(false);
        testUser.setFailedLoginAttempts(0);
        testUser.setRoles(new HashSet<>());

        // Set up user role
        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("ROLE_USER");
    }

    @Test
    void register_ValidRequest_ReturnsAuthResponse() {
        // given
        when(userRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(validRegisterRequest.getPassword())).thenReturn("$2a$12$hashedPassword");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(anyString())).thenReturn("accessToken123");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        // when
        AuthResponse response = authService.register(validRegisterRequest);

        // then
        assertNotNull(response);
        assertEquals("accessToken123", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getRefreshToken());
        assertNotNull(response.getUser());
        assertEquals(testUser.getEmail(), response.getUser().getEmail());

        verify(userRepository, times(1)).existsByEmail(validRegisterRequest.getEmail());
        verify(passwordEncoder, times(1)).encode(validRegisterRequest.getPassword());
        verify(userRepository, times(1)).save(any(User.class));
        verify(jwtService, times(1)).generateToken(anyString());
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    void register_EmailAlreadyExists_ThrowsException() {
        // given
        when(userRepository.existsByEmail(validRegisterRequest.getEmail())).thenReturn(true);

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertEquals("Email already registered", exception.getMessage());
        verify(userRepository, times(1)).existsByEmail(validRegisterRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_WeakPassword_ThrowsException() {
        // given
        validRegisterRequest.setPassword("weak");
        validRegisterRequest.setConfirmPassword("weak");

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertTrue(exception.getMessage().contains("Password must be at least 12 characters"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_PasswordMismatch_ThrowsException() {
        // given
        validRegisterRequest.setConfirmPassword("DifferentPass123!");

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertEquals("Passwords do not match", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_NoUppercaseInPassword_ThrowsException() {
        // given
        validRegisterRequest.setPassword("securepass123!");
        validRegisterRequest.setConfirmPassword("securepass123!");

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertTrue(exception.getMessage().contains("Password must be at least 12 characters"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ValidCredentials_ReturnsAuthResponse() {
        // given
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser.getEmail())).thenReturn("accessToken123");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(refreshTokenRepository.save(any())).thenReturn(null);
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when
        AuthResponse response = authService.login(validLoginRequest);

        // then
        assertNotNull(response);
        assertEquals("accessToken123", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getRefreshToken());
        assertNotNull(response.getUser());

        verify(userRepository, times(1)).findByEmail(validLoginRequest.getEmail());
        verify(passwordEncoder, times(1)).matches(validLoginRequest.getPassword(), testUser.getPasswordHash());
        verify(jwtService, times(1)).generateToken(testUser.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
        verify(authAuditLogRepository, times(1)).save(any());
    }

    @Test
    void login_InvalidCredentials_ThrowsException() {
        // given
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), testUser.getPasswordHash())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(userRepository, times(1)).save(any(User.class)); // Failed attempts incremented
        verify(authAuditLogRepository, times(1)).save(any()); // LOGIN_FAILURE logged
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        // given
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.empty());

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(userRepository, times(1)).findByEmail(validLoginRequest.getEmail());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_AccountLocked_ThrowsException() {
        // given
        testUser.setAccountLocked(true);
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Account is locked", exception.getMessage());
        verify(authAuditLogRepository, times(1)).save(any()); // LOGIN_FAILURE logged
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_AccountDisabled_ThrowsException() {
        // given
        testUser.setAccountEnabled(false);
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Account is disabled", exception.getMessage());
        verify(authAuditLogRepository, times(1)).save(any()); // LOGIN_FAILURE logged
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_TooManyFailedAttempts_LocksAccount() {
        // given
        testUser.setFailedLoginAttempts(9); // One more failure will lock the account
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), testUser.getPasswordHash())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            assertTrue(savedUser.getAccountLocked());
            assertEquals(10, savedUser.getFailedLoginAttempts());
            return savedUser;
        });
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when/then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(validLoginRequest)
        );

        assertTrue(exception.getMessage().contains("Account has been locked"));
        verify(userRepository, times(1)).save(any(User.class));
        verify(authAuditLogRepository, times(1)).save(any()); // ACCOUNT_LOCKED only
    }

    @Test
    void login_SuccessfulLogin_ResetsFailedAttempts() {
        // given
        testUser.setFailedLoginAttempts(5);
        when(userRepository.findByEmail(validLoginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(validLoginRequest.getPassword(), testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser.getEmail())).thenReturn("accessToken123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            assertEquals(0, savedUser.getFailedLoginAttempts());
            return savedUser;
        });
        when(refreshTokenRepository.save(any())).thenReturn(null);
        when(authAuditLogRepository.save(any())).thenReturn(null);

        // when
        AuthResponse response = authService.login(validLoginRequest);

        // then
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }
}
