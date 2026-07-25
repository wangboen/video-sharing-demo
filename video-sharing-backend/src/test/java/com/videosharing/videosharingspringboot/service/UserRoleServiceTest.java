package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
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
    @Mock StaffAccountRepository staffRepository;
    @Mock PasswordEncoder encoder;
    private UserService service;
    private StaffAccountService staffService;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, encoder);
        staffService = new StaffAccountService(staffRepository, encoder);
    }

    @Test
    void publicRegistrationAlwaysCreatesAUser() {
        stubAccountCreation();
        User user = service.registerUser("creator", "creator@example.com", "password123");
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    void userLoginRejectsUnknownStaffCredentialsBecauseStaffLivesInAnotherTable() {
        when(repository.findByUsername("reviewer")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.loginUser("reviewer", "password123"));
    }

    @Test
    void staffLoginAcceptsReviewerCredentialsFromStaffTable() {
        when(staffRepository.findByUsername("reviewer")).thenReturn(Optional.of(staff(Role.REVIEWER)));
        when(encoder.matches("password123", "encoded")).thenReturn(true);
        assertEquals(Role.REVIEWER, staffService.loginStaff("reviewer", "password123").getRole());
    }

    @Test
    void staffServiceRejectsPublicUserRole() {
        assertThrows(IllegalArgumentException.class,
                () -> staffService.createAccount("staff", "staff@example.com", "password123", Role.USER));
    }

    @Test
    void adminCreatesReviewerWithoutAllowingRoleEscalation() {
        stubStaffAccountCreation();
        StaffAccount reviewer = staffService.createReviewer("reviewer", "reviewer@example.com", "password123");
        assertEquals(Role.REVIEWER, reviewer.getRole());
    }

    private User user(Role role) {
        User user = new User();
        user.setUsername("reviewer");
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    private StaffAccount staff(Role role) {
        StaffAccount staff = new StaffAccount();
        staff.setUsername("reviewer");
        staff.setPassword("encoded");
        staff.setRole(role);
        return staff;
    }

    private void stubAccountCreation() {
        when(encoder.encode(any())).thenReturn("encoded");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubStaffAccountCreation() {
        when(encoder.encode(any())).thenReturn("encoded");
        when(staffRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
