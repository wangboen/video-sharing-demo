package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StaffAccountService {
    private final StaffAccountRepository staffAccounts;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(StaffAccountRepository staffAccounts, PasswordEncoder passwordEncoder) {
        this.staffAccounts = staffAccounts;
        this.passwordEncoder = passwordEncoder;
    }

    public StaffAccount createReviewer(String username, String email, String password) {
        return createAccount(username, email, password, Role.REVIEWER);
    }

    public StaffAccount createAccount(String username, String email, String password, Role role) {
        if (role != Role.REVIEWER && role != Role.ADMIN) {
            throw new IllegalArgumentException("Staff role must be REVIEWER or ADMIN");
        }
        username = username.trim();
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (staffAccounts.existsByUsername(username)) {
            throw new RuntimeException("Username already taken!");
        }
        if (email != null && staffAccounts.existsByEmail(email)) {
            throw new RuntimeException("Email already in use!");
        }

        StaffAccount staff = new StaffAccount();
        staff.setUsername(username);
        staff.setEmail(email);
        staff.setPassword(passwordEncoder.encode(password));
        staff.setRole(role);
        return staffAccounts.save(staff);
    }

    public StaffAccount loginStaff(String username, String password) {
        Optional<StaffAccount> account = staffAccounts.findByUsername(username.trim());
        if (account.isEmpty() || !passwordEncoder.matches(password, account.get().getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }
        return account.get();
    }

    public Optional<StaffAccount> findByUsername(String username) {
        return staffAccounts.findByUsername(username);
    }

    public Optional<StaffAccount> findStaffById(Long id) {
        return staffAccounts.findById(id);
    }

    public List<StaffAccount> listReviewers() {
        return staffAccounts.findAllByRoleOrderByUsernameAsc(Role.REVIEWER);
    }
}
