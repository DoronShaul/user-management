package com.doron.shaul.usermanagement.service;

import com.doron.shaul.usermanagement.model.User;
import com.doron.shaul.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        return userRepository.save(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User updateUser(Long id, User user, Authentication auth) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String authenticatedEmail = auth.getName();
        if (!existing.getEmail().equals(authenticatedEmail)) {
            throw new AccessDeniedException("Cannot update other users");
        }

        existing.setName(user.getName());
        return userRepository.save(existing);
    }

    public List<User> findByNameContaining(String name) {
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    @Transactional
    public void deleteUser(Long id, Authentication auth) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String authenticatedEmail = auth.getName();
        if (!existing.getEmail().equals(authenticatedEmail)) {
            throw new AccessDeniedException("Cannot delete other users");
        }

        userRepository.deleteById(id);
    }
}
