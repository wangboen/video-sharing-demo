package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String username, String email, String password) {
        return createAccount(username, email, password, Role.USER);
    }

    public User createReviewer(String username, String email, String password) {
        return createAccount(username, email, password, Role.REVIEWER);
    }

    public User createAccount(String username, String email, String password, Role role) {
        username = username.trim();
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken!");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already in use!");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password)); // Encode password
        user.setRole(role);
        return userRepository.save(user);
    }

    public User loginUser(String username, String password) {
        return authenticate(username, password, Role.USER);
    }

    public User loginStaff(String username, String password) {
        User user = authenticate(username, password, null);
        if (user.getRole() != Role.REVIEWER && user.getRole() != Role.ADMIN) {
            throw new RuntimeException("Invalid username or password");
        }
        return user;
    }

    private User authenticate(String username, String password, Role requiredRole) {
        Optional<User> userOptional = userRepository.findByUsername(username.trim());
        if (userOptional.isEmpty() || !passwordEncoder.matches(password, userOptional.get().getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }
        User user = userOptional.get();
        if (requiredRole != null && user.getRole() != requiredRole) {
            throw new RuntimeException("Invalid username or password");
        }
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> listReviewers() {
        return userRepository.findAllByRoleOrderByUsernameAsc(Role.REVIEWER);
    }
}
