package com.doron.shaul.usermanagement.controller;

import com.doron.shaul.usermanagement.dto.AuthResponse;
import com.doron.shaul.usermanagement.dto.LoginRequest;
import com.doron.shaul.usermanagement.dto.RegisterRequest;
import com.doron.shaul.usermanagement.dto.UserDTO;
import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.config.SecurityConfig;
import com.doron.shaul.usermanagement.repository.RefreshTokenRepository;
import com.doron.shaul.usermanagement.repository.UserRepository;
import com.doron.shaul.usermanagement.security.JwtAuthenticationFilter;
import com.doron.shaul.usermanagement.security.JwtService;
import com.doron.shaul.usermanagement.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private AuthResponse authResponse;
    private User testUser;
    private UserDTO testUserDTO;

    @BeforeEach
    void setUp() {
        // Setup test data
        validRegisterRequest = new RegisterRequest(
                "John Doe",
                "john@example.com",
                "SecurePass123!",
                "SecurePass123!"
        );

        validLoginRequest = new LoginRequest(
                "john@example.com",
                "SecurePass123!"
        );

        testUserDTO = new UserDTO(
                1L,
                "John Doe",
                "john@example.com",
                LocalDateTime.now()
        );

        authResponse = new AuthResponse(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
                "refresh-token-uuid",
                900000L,
                testUserDTO
        );

        testUser = new User();
        testUser.setId(1L);
        testUser.setName("John Doe");
        testUser.setEmail("john@example.com");
        testUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void registerUser_ValidRequest_ReturnsCreatedAndAuthResponse() throws Exception {
        // given
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        // when/then
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value(authResponse.getAccessToken()))
                .andExpect(jsonPath("$.refreshToken").value(authResponse.getRefreshToken()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900000))
                .andExpect(jsonPath("$.user.email").value("john@example.com"));

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    void registerUser_DuplicateEmail_ReturnsBadRequest() throws Exception {
        // given
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Email already exists"));

        // when/then
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    void loginUser_ValidCredentials_ReturnsOkAndAuthResponse() throws Exception {
        // given
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        // when/then
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(authResponse.getAccessToken()))
                .andExpect(jsonPath("$.refreshToken").value(authResponse.getRefreshToken()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("john@example.com"));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    void loginUser_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        // given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid credentials"));

        // when/then
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized());

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void logoutUser_AuthenticatedUser_ReturnsOk() throws Exception {
        // given
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        // when/then
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(userRepository, times(1)).findByEmail("john@example.com");
        verify(refreshTokenRepository, times(1)).deleteByUserId(1L);
    }

    @Test
    @WithMockUser(username = "nonexistent@example.com")
    void logoutUser_NonexistentUser_ReturnsNotFound() throws Exception {
        // given
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // when/then
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(userRepository, times(1)).findByEmail("nonexistent@example.com");
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void getCurrentUser_AuthenticatedUser_ReturnsUserDTO() throws Exception {
        // given
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        // when/then
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));

        verify(userRepository, times(1)).findByEmail("john@example.com");
    }

    @Test
    @WithMockUser(username = "nonexistent@example.com")
    void getCurrentUser_NonexistentUser_ReturnsNotFound() throws Exception {
        // given
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // when/then
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isNotFound());

        verify(userRepository, times(1)).findByEmail("nonexistent@example.com");
    }
}
