package com.doron.shaul.usermanagement.controller;

import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void createUser_Success() throws Exception {
        User user = new User();
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName("Jane Doe");
        savedUser.setEmail("jane@example.com");

        when(userService.createUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void createUser_EmailAlreadyExists_ReturnsBadRequest() throws Exception {
        User user = new User();
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");

        when(userService.createUser(any(User.class)))
                .thenThrow(new IllegalArgumentException("Email already exists"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email already exists"));
    }

    @Test
    void getUser_UserExists_ReturnsOk() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");

        when(userService.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void getUser_UserNotFound_ReturnsNotFound() throws Exception {
        when(userService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_Success() throws Exception {
        User request = new User();
        request.setName("Updated Name");
        request.setEmail("jane@example.com");

        User updatedUser = new User();
        updatedUser.setId(1L);
        updatedUser.setName("Updated Name");
        updatedUser.setEmail("jane@example.com");

        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void updateUser_UserNotFound_ReturnsNotFound() throws Exception {
        User request = new User();
        request.setName("Updated Name");
        request.setEmail("jane@example.com");

        when(userService.updateUser(eq(99L), any(User.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        mockMvc.perform(put("/api/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_Success() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_UserNotFound_ReturnsNotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .when(userService).deleteUser(99L);

        mockMvc.perform(delete("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUsersByName_MatchesFound_ReturnsOkWithList() throws Exception {
        User user1 = new User();
        user1.setId(1L);
        user1.setName("Doron");
        user1.setEmail("doron@example.com");

        User user2 = new User();
        user2.setId(2L);
        user2.setName("Doris");
        user2.setEmail("doris@example.com");

        when(userService.findByNameContaining("do")).thenReturn(Arrays.asList(user1, user2));

        mockMvc.perform(get("/api/users").param("name", "do"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Doron"))
                .andExpect(jsonPath("$[0].email").value("doron@example.com"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Doris"))
                .andExpect(jsonPath("$[1].email").value("doris@example.com"));
    }

    @Test
    void getUsersByName_NoMatches_ReturnsOkWithEmptyList() throws Exception {
        when(userService.findByNameContaining("xyz")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/users").param("name", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getUsersByName_MissingParam_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isBadRequest());
    }
}
