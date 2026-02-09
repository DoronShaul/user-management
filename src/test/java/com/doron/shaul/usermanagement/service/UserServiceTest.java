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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    void updateUser_AuthenticatedAsOwner_Success() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("John Doe");
        existing.setEmail("john@example.com");

        User updateRequest = new User();
        updateRequest.setName("Jane Doe");
        updateRequest.setEmail("ignored@example.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("john@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(1L, updateRequest, auth);

        assertEquals("Jane Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    void updateUser_AuthenticatedAsNonOwner_ThrowsAccessDenied() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("John Doe");
        existing.setEmail("john@example.com");

        User updateRequest = new User();
        updateRequest.setName("Jane Doe");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("hacker@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> userService.updateUser(1L, updateRequest, auth)
        );

        assertEquals("Cannot update other users", exception.getMessage());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_UserNotFound_ThrowsException() {
        User updateRequest = new User();
        updateRequest.setName("Jane Doe");

        Authentication auth = mock(Authentication.class);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUser(99L, updateRequest, auth)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, times(1)).findById(99L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUser_AuthenticatedAsOwner_Success() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("John Doe");
        existing.setEmail("john@example.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("john@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        userService.deleteUser(1L, auth);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteUser_AuthenticatedAsNonOwner_ThrowsAccessDenied() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("John Doe");
        existing.setEmail("john@example.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("hacker@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> userService.deleteUser(1L, auth)
        );

        assertEquals("Cannot delete other users", exception.getMessage());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteUser_UserNotFound_ThrowsException() {
        Authentication auth = mock(Authentication.class);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.deleteUser(99L, auth)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, times(1)).findById(99L);
        verify(userRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void findByNameContaining_MatchesFound_ReturnsList() {
        User user1 = new User();
        user1.setId(1L);
        user1.setName("Doron");
        user1.setEmail("doron@example.com");

        User user2 = new User();
        user2.setId(2L);
        user2.setName("Doris");
        user2.setEmail("doris@example.com");

        when(userRepository.findByNameContainingIgnoreCase("do")).thenReturn(Arrays.asList(user1, user2));

        List<User> result = userService.findByNameContaining("do");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Doron", result.get(0).getName());
        assertEquals("Doris", result.get(1).getName());
        verify(userRepository, times(1)).findByNameContainingIgnoreCase("do");
    }

    @Test
    void findByNameContaining_NoMatches_ReturnsEmptyList() {
        when(userRepository.findByNameContainingIgnoreCase("xyz")).thenReturn(Collections.emptyList());

        List<User> result = userService.findByNameContaining("xyz");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository, times(1)).findByNameContainingIgnoreCase("xyz");
    }
}
