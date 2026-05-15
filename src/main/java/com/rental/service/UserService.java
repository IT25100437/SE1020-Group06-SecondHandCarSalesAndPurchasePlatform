package com.rental.service;

import com.rental.model.User;
import com.rental.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Component 01: User Management - Service layer
// Encapsulation: all business rules enforced here before touching the repository
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // CREATE - Register a new user account
    public User register(String name, String email, String phone, String address,
                         String password, String role) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required.");
        if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (userRepository.findByEmail(email).isPresent())
            throw new IllegalArgumentException("Email already registered.");

        User user = new User();
        user.setName(name.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPhone(phone == null ? "" : phone.trim());
        user.setAddress(address == null ? "" : address.trim());
        user.setRole(normalizeRole(role));
        user.setPasswordHash(password); // plain text for file-based demo
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    // READ - Login (authenticate user)
    public User login(String email, String password) {
        if (email == null || password == null)
            throw new IllegalArgumentException("Invalid email or password.");
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));
        if (!user.getPasswordHash().equals(password))
            throw new IllegalArgumentException("Invalid email or password.");
        return user;
    }

    // READ - Find by ID
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    // READ - Find by email
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // READ - Search users by name, email, or role (admin use)
    public List<User> searchUsers(String query) {
        List<User> all = userRepository.findAll();
        if (query == null || query.isBlank()) return all;
        String q = query.trim().toLowerCase();
        return all.stream().filter(u ->
                (u.getName() != null && u.getName().toLowerCase().contains(q)) ||
                        (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                        (u.getRole() != null && u.getRole().toLowerCase().contains(q)) ||
                        (u.getPhone() != null && u.getPhone().contains(q))
        ).collect(Collectors.toList());
    }

    // READ - Get all users (admin)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // UPDATE - User updates their own profile (contact info, password)
    public User updateProfile(Long id, String name, String email, String phone,
                              String address, String password) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (name != null && !name.isBlank()) user.setName(name.trim());
        if (email != null && !email.isBlank()) {
            Optional<User> existing = userRepository.findByEmail(email.trim().toLowerCase());
            if (existing.isPresent() && !existing.get().getId().equals(id))
                throw new IllegalArgumentException("Email already in use.");
            user.setEmail(email.trim().toLowerCase());
        }
        if (phone != null && !phone.isBlank()) user.setPhone(phone.trim());
        if (address != null && !address.isBlank()) user.setAddress(address.trim());
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
            user.setPasswordHash(password);
        }
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.update(user);
    }

    // DELETE - User deletes their own account
    public void deleteAccount(Long id) {
        if (!userRepository.deleteById(id))
            throw new IllegalArgumentException("User not found.");
    }

    // DELETE - Admin deletes a user account (inactive or fraudulent)
    public void deleteUserByAdmin(Long id) {
        if (!userRepository.deleteById(id))
            throw new IllegalArgumentException("User not found.");
    }

    // Helper - Normalize role string
    private String normalizeRole(String role) {
        if (role == null) return "BUYER";
        return switch (role.trim().toUpperCase()) {
            case "SELLER" -> "SELLER";
            case "DEALER" -> "DEALER";
            default -> "BUYER";
        };
    }
}
