package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AdminBootstrapTest {

    private final StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void allAbsentSkipsBootstrap() {
        new AdminBootstrap(staffAccounts, encoder, "", "", "").run();
        verifyNoInteractions(staffAccounts, encoder);
    }

    @Test
    void partialConfigurationFailsStartup() {
        AdminBootstrap bootstrap = new AdminBootstrap(staffAccounts, encoder, "admin", "", "password123");
        assertThrows(IllegalStateException.class, bootstrap::run);
    }

    @Test
    void completeConfigurationCreatesAdminExactlyOnce() {
        when(staffAccounts.findByUsername("admin")).thenReturn(Optional.empty());
        when(encoder.encode("password123")).thenReturn("encoded");
        when(staffAccounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminBootstrap bootstrap = new AdminBootstrap(staffAccounts, encoder, " admin ",
                "ADMIN@example.com", "password123");
        bootstrap.run();

        ArgumentCaptor<StaffAccount> captor = ArgumentCaptor.forClass(StaffAccount.class);
        verify(staffAccounts).save(captor.capture());
        StaffAccount saved = captor.getValue();
        assertEquals(Role.ADMIN, saved.getRole());
        assertEquals("admin@example.com", saved.getEmail());
    }

    @Test
    void existingAdminIsNotOverwritten() {
        StaffAccount admin = new StaffAccount();
        admin.setRole(Role.ADMIN);
        when(staffAccounts.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AdminBootstrap(staffAccounts, encoder, "admin", "admin@example.com", "password123").run();

        verify(staffAccounts, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void collidingNonAdminUsernameFailsInsteadOfPromotion() {
        StaffAccount reviewer = new StaffAccount();
        reviewer.setRole(Role.REVIEWER);
        when(staffAccounts.findByUsername("admin")).thenReturn(Optional.of(reviewer));

        AdminBootstrap bootstrap = new AdminBootstrap(staffAccounts, encoder, "admin",
                "admin@example.com", "password123");
        assertThrows(IllegalStateException.class, bootstrap::run);
    }
}
