package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, encoder);
    }

    @Test
    void publicRegistrationAlwaysCreatesAUser() {
        stubAccountCreation();
        User user = service.registerUser("creator", "creator@example.com", "password123");
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    void userLoginRejectsReviewerCredentials() {
        when(repository.findByUsername("reviewer")).thenReturn(Optional.of(user(Role.REVIEWER)));
        when(encoder.matches("password123", "encoded")).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.loginUser("reviewer", "password123"));
    }

    @Test
    void staffLoginAcceptsReviewerCredentials() {
        when(repository.findByUsername("reviewer")).thenReturn(Optional.of(user(Role.REVIEWER)));
        when(encoder.matches("password123", "encoded")).thenReturn(true);
        assertEquals(Role.REVIEWER, service.loginStaff("reviewer", "password123").getRole());
    }

    @Test
    void adminCreatesReviewerWithoutAllowingRoleEscalation() {
        stubAccountCreation();
        User reviewer = service.createReviewer("reviewer", "reviewer@example.com", "password123");
        assertEquals(Role.REVIEWER, reviewer.getRole());
    }

    private User user(Role role) {
        User user = new User();
        user.setUsername("reviewer");
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    private void stubAccountCreation() {
        when(encoder.encode(any())).thenReturn("encoded");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
