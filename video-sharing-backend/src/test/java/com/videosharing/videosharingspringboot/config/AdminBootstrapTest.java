package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void allAbsentSkipsBootstrap() {
        new AdminBootstrap(users, encoder, "", "", "").run();
        verifyNoInteractions(users, encoder);
    }

    @Test
    void partialConfigurationFailsStartup() {
        AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, "admin", "", "password123");
        assertThrows(IllegalStateException.class, bootstrap::run);
    }

    @Test
    void completeConfigurationCreatesAdminExactlyOnce() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        when(encoder.encode("password123")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, " admin ",
                "ADMIN@example.com", "password123");
        bootstrap.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.ADMIN, saved.getRole());
        assertEquals("admin@example.com", saved.getEmail());
    }

    @Test
    void existingAdminIsNotOverwritten() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AdminBootstrap(users, encoder, "admin", "admin@example.com", "password123").run();

        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void collidingNonAdminUsernameFailsInsteadOfPromotion() {
        User user = new User();
        user.setRole(Role.USER);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, "admin",
                "admin@example.com", "password123");
        assertThrows(IllegalStateException.class, bootstrap::run);
    }
}
