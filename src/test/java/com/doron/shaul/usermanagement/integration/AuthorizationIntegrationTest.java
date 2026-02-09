package com.doron.shaul.usermanagement.integration;

import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.repository.UserRepository;
import com.doron.shaul.usermanagement.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        // Clear database
        userRepository.deleteAll();

        // Create User 1
        user1 = new User();
        user1.setName("Alice Smith");
        user1.setEmail("alice@example.com");
        user1.setPasswordHash(passwordEncoder.encode("password123"));
        user1 = userRepository.save(user1);

        // Create User 2
        user2 = new User();
        user2.setName("Bob Jones");
        user2.setEmail("bob@example.com");
        user2.setPasswordHash(passwordEncoder.encode("password456"));
        user2 = userRepository.save(user2);

        // Generate JWT tokens for each user
        token1 = jwtService.generateToken(user1.getEmail());
        token2 = jwtService.generateToken(user2.getEmail());
    }

    @Test
    void updateUser_AuthenticatedAsOwner_Success() throws Exception {
        // given: User 1 wants to update their own profile
        User updateRequest = new User();
        updateRequest.setName("Alice Updated");
        updateRequest.setEmail("ignored@example.com");

        // when/then: Should succeed with 200 OK
        mockMvc.perform(put("/api/users/" + user1.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user1.getId()))
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.com")); // Email should not change
    }

    @Test
    void updateUser_AuthenticatedAsOtherUser_ReturnsForbidden() throws Exception {
        // given: User 1 wants to update User 2's profile
        User updateRequest = new User();
        updateRequest.setName("Hacked Name");
        updateRequest.setEmail("hacker@example.com");

        // when/then: Should fail with 403 Forbidden
        mockMvc.perform(put("/api/users/" + user2.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // given: Request without authentication token
        User updateRequest = new User();
        updateRequest.setName("Updated Name");

        // when/then: Should fail with 403 Forbidden (Spring Security default for unauthenticated access)
        mockMvc.perform(put("/api/users/" + user1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_AuthenticatedAsOwner_Success() throws Exception {
        // given: User 1 wants to delete their own account

        // when/then: Should succeed with 204 No Content
        mockMvc.perform(delete("/api/users/" + user1.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // Verify user is actually deleted
        mockMvc.perform(get("/api/users/" + user1.getId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_AuthenticatedAsOtherUser_ReturnsForbidden() throws Exception {
        // given: User 1 wants to delete User 2's account

        // when/then: Should fail with 403 Forbidden
        mockMvc.perform(delete("/api/users/" + user2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isForbidden());

        // Verify user 2 still exists
        mockMvc.perform(get("/api/users/" + user2.getId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void deleteUser_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // given: Request without authentication token

        // when/then: Should fail with 403 Forbidden (Spring Security default for unauthenticated access)
        mockMvc.perform(delete("/api/users/" + user1.getId()))
                .andExpect(status().isForbidden());

        // Verify user still exists
        mockMvc.perform(get("/api/users/" + user1.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_WithAuthentication_Success() throws Exception {
        // given: User 1 wants to view User 2's profile (read operations allowed for any authenticated user)

        // when/then: Should succeed with 200 OK
        mockMvc.perform(get("/api/users/" + user2.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user2.getId()))
                .andExpect(jsonPath("$.name").value("Bob Jones"))
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void getUser_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // given: Request without authentication token

        // when/then: Should fail with 403 Forbidden (Spring Security default for unauthenticated access)
        mockMvc.perform(get("/api/users/" + user1.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchUsers_WithAuthentication_Success() throws Exception {
        // given: User 1 wants to search for users by name

        // when/then: Should succeed with 200 OK
        mockMvc.perform(get("/api/users")
                        .param("name", "alice")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice Smith"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    void searchUsers_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // given: Request without authentication token

        // when/then: Should fail with 403 Forbidden (Spring Security default for unauthenticated access)
        mockMvc.perform(get("/api/users").param("name", "alice"))
                .andExpect(status().isForbidden());
    }
}
