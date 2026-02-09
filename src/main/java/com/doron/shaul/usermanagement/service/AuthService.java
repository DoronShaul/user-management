package com.doron.shaul.usermanagement.service;

import com.doron.shaul.usermanagement.dto.AuthResponse;
import com.doron.shaul.usermanagement.dto.LoginRequest;
import com.doron.shaul.usermanagement.dto.RegisterRequest;
import com.doron.shaul.usermanagement.dto.UserDTO;
import com.doron.shaul.usermanagement.model.*;
import com.doron.shaul.usermanagement.repository.*;
import com.doron.shaul.usermanagement.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthAuditLogRepository authAuditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$"
    );

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate password matches confirmPassword
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Validate password strength
        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            throw new IllegalArgumentException(
                    "Password must be at least 12 characters long and contain at least one uppercase letter, " +
                    "one lowercase letter, one digit, and one special character (@$!%*?&)"
            );
        }

        // Check email not already registered
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // Create user with default ROLE_USER
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordHash);
        user.setAccountEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setPasswordChangedAt(LocalDateTime.now());

        // Assign default role
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER not found"));
        user.getRoles().add(userRole);

        user = userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());

        // Return AuthResponse
        UserDTO userDTO = new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
        return new AuthResponse(accessToken, refreshToken, accessTokenExpiration / 1000, userDTO);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Check account not locked
        if (user.getAccountLocked()) {
            logAuthEvent(user.getId(), user.getEmail(), "LOGIN_FAILURE", "FAILED", "Account is locked");
            throw new IllegalArgumentException("Account is locked");
        }

        // Check account enabled
        if (!user.getAccountEnabled()) {
            logAuthEvent(user.getId(), user.getEmail(), "LOGIN_FAILURE", "FAILED", "Account is disabled");
            throw new IllegalArgumentException("Account is disabled");
        }

        // Validate password with BCrypt
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Increment failed attempts
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            // Lock account after 10 failed attempts
            if (user.getFailedLoginAttempts() >= 10) {
                user.setAccountLocked(true);
                userRepository.save(user);
                logAuthEvent(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", "SUCCESS",
                           "Account locked after 10 failed login attempts");
                throw new IllegalArgumentException("Account has been locked due to too many failed login attempts");
            }

            userRepository.save(user);
            logAuthEvent(user.getId(), user.getEmail(), "LOGIN_FAILURE", "FAILED", "Invalid password");
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Reset failed attempts on success
        user.setFailedLoginAttempts(0);

        // Update lastLoginAt
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());

        // Log LOGIN_SUCCESS
        logAuthEvent(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "SUCCESS", null);

        // Return AuthResponse
        UserDTO userDTO = new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
        return new AuthResponse(accessToken, refreshToken, accessTokenExpiration / 1000, userDTO);
    }

    private String generateRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUserId(userId);
        refreshToken.setExpiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        refreshToken.setIsRevoked(false);

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    private void logAuthEvent(Long userId, String username, String eventType, String eventStatus, String failureReason) {
        AuthAuditLog log = new AuthAuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setEventType(eventType);
        log.setEventStatus(eventStatus);
        log.setFailureReason(failureReason);

        authAuditLogRepository.save(log);
    }
}
