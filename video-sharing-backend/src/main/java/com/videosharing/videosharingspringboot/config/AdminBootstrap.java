package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final StaffAccountRepository staffAccounts;
    private final PasswordEncoder encoder;
    private final String username;
    private final String email;
    private final String password;

    @Autowired
    public AdminBootstrap(StaffAccountRepository staffAccounts,
                          PasswordEncoder encoder,
                          @Value("${ADMIN_USERNAME:}") String username,
                          @Value("${ADMIN_EMAIL:}") String email,
                          @Value("${ADMIN_PASSWORD:}") String password) {
        this.staffAccounts = staffAccounts;
        this.encoder = encoder;
        this.username = username == null ? "" : username.trim();
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int configured = (username.isBlank() ? 0 : 1)
                + (email.isBlank() ? 0 : 1)
                + (password.isBlank() ? 0 : 1);
        if (configured == 0) {
            log.warn("Initial admin bootstrap is not configured; ADMIN_USERNAME, ADMIN_EMAIL, and ADMIN_PASSWORD are all absent");
            return;
        }
        if (configured != 3) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME, ADMIN_EMAIL, and ADMIN_PASSWORD must be configured together");
        }
        validate();

        var existing = staffAccounts.findByUsername(username);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new IllegalStateException("ADMIN_USERNAME collides with a non-admin account");
            }
            log.info("Initial admin account already exists; leaving it unchanged");
            return;
        }
        if (staffAccounts.existsByEmail(email)) {
            throw new IllegalStateException("ADMIN_EMAIL is already used by another account");
        }

        StaffAccount admin = new StaffAccount();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        staffAccounts.save(admin);
        log.info("Created initial admin account '{}'", username);
    }

    private void validate() {
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalStateException("ADMIN_USERNAME must contain 3 to 50 characters");
        }
        if (email.length() > 100 || !email.contains("@")) {
            throw new IllegalStateException("ADMIN_EMAIL is invalid");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain 8 to 72 characters");
        }
    }
}
