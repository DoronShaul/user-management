package com.doron.shaul.usermanagement.service;

import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setName("John Doe");
        testUser.setEmail("john@example.com");
    }

    @Test
    void createUser_Success() {
        when(userRepository.existsByEmail(testUser.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.createUser(testUser);

        assertNotNull(result);
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        verify(userRepository, times(1)).existsByEmail(testUser.getEmail());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void createUser_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(testUser.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(testUser)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository, times(1)).existsByEmail(testUser.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findById_UserExists_ReturnsUser() {
        testUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("John Doe", result.get().getName());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void findById_UserNotFound_ReturnsEmpty() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<User> result = userService.findById(99L);

        assertTrue(result.isEmpty());
        verify(userRepository, times(1)).findById(99L);
    }

    @Test
    void updateUser_UserExists_UpdatesNameOnly() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("John Doe");
        existing.setEmail("john@example.com");

        User updateRequest = new User();
        updateRequest.setName("Jane Doe");
        updateRequest.setEmail("ignored@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(1L, updateRequest);

        assertEquals("Jane Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    void updateUser_UserNotFound_ThrowsException() {
        User updateRequest = new User();
        updateRequest.setName("Jane Doe");

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUser(99L, updateRequest)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, times(1)).findById(99L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUser_UserExists_DeletesUser() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).existsById(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteUser_UserNotFound_ThrowsException() {
        when(userRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.deleteUser(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, times(1)).existsById(99L);
        verify(userRepository, never()).deleteById(any(Long.class));
    }
}
