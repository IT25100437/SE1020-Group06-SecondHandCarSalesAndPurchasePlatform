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

    // Repository object used to access user data
    private final UserRepository userRepository;

    // Constructor Injection for UserRepository
    public UserService(UserRepository userRepository) {

        // Assign repository object
        this.userRepository = userRepository;
    }

    // CREATE - Register a new user account
    public User register(String name, String email, String phone, String address,
                         String password, String role) {

        // Check whether name is empty or null
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required.");

        // Check whether email is empty or null
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required.");

        // Check whether password is empty or null
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("Password is required.");

        // Check password minimum length
        if (password.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");

        // Check whether email already exists in the system
        if (userRepository.findByEmail(email).isPresent())
            throw new IllegalArgumentException("Email already registered.");

        // Create new User object
        User user = new User();

        // Set user name after removing extra spaces
        user.setName(name.trim());

        // Set email in lowercase and remove extra spaces
        user.setEmail(email.trim().toLowerCase());

        // Set phone number, or empty string if null
        user.setPhone(phone == null ? "" : phone.trim());

        // Set address, or empty string if null
        user.setAddress(address == null ? "" : address.trim());

        // Set normalized user role
        user.setRole(normalizeRole(role));

        // Store password (plain text for demo purposes)
        user.setPasswordHash(password);

        // Set account created date and time
        user.setCreatedAt(LocalDateTime.now());

        // Set account updated date and time
        user.setUpdatedAt(LocalDateTime.now());

        // Save user and return saved object
        return userRepository.save(user);
    }

    // READ - Login (authenticate user)
    public User login(String email, String password) {

        // Validate email and password inputs
        if (email == null || password == null)
            throw new IllegalArgumentException("Invalid email or password.");

        // Find user by email
        User user = userRepository.findByEmail(email.trim().toLowerCase())

                // Throw exception if email not found
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        // Compare stored password with entered password
        if (!user.getPasswordHash().equals(password))
            throw new IllegalArgumentException("Invalid email or password.");

        // Return authenticated user
        return user;
    }

    // READ - Find by ID
    public Optional<User> findById(Long id) {

        // Return user by ID
        return userRepository.findById(id);
    }

    // READ - Find by email
    public Optional<User> findByEmail(String email) {

        // Return user by email
        return userRepository.findByEmail(email);
    }

    // READ - Search users by name, email, or role (admin use)
    public List<User> searchUsers(String query) {

        // Get all users from repository
        List<User> all = userRepository.findAll();

        // If query is empty return all users
        if (query == null || query.isBlank())
            return all;

        // Convert query to lowercase for case-insensitive search
        String q = query.trim().toLowerCase();

        // Filter matching users
        return all.stream().filter(u ->

                        // Match by name
                        (u.getName() != null && u.getName().toLowerCase().contains(q)) ||

                                // Match by email
                                (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||

                                // Match by role
                                (u.getRole() != null && u.getRole().toLowerCase().contains(q)) ||

                                // Match by phone number
                                (u.getPhone() != null && u.getPhone().contains(q))

                )

                // Convert filtered stream back to list
                .collect(Collectors.toList());
    }

    // READ - Get all users (admin)
    public List<User> getAllUsers() {

        // Return all users
        return userRepository.findAll();
    }

    // UPDATE - User updates their own profile
    public User updateProfile(Long id, String name, String email, String phone,
                              String address, String password) {

        // Find user by ID
        User user = userRepository.findById(id)

                // Throw exception if user not found
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // Update name if provided
        if (name != null && !name.isBlank())
            user.setName(name.trim());

        // Update email if provided
        if (email != null && !email.isBlank()) {

            // Check whether email already exists
            Optional<User> existing = userRepository.findByEmail(email.trim().toLowerCase());

            // Prevent duplicate email usage
            if (existing.isPresent() && !existing.get().getId().equals(id))
                throw new IllegalArgumentException("Email already in use.");

            // Set updated email
            user.setEmail(email.trim().toLowerCase());
        }

        // Update phone number if provided
        if (phone != null && !phone.isBlank())
            user.setPhone(phone.trim());

        // Update address if provided
        if (address != null && !address.isBlank())
            user.setAddress(address.trim());

        // Update password if provided
        if (password != null && !password.isBlank()) {

            // Validate password length
            if (password.length() < 6)
                throw new IllegalArgumentException("Password must be at least 6 characters.");

            // Set new password
            user.setPasswordHash(password);
        }

        // Update modified date and time
        user.setUpdatedAt(LocalDateTime.now());

        // Save updated user
        return userRepository.update(user);
    }

    // DELETE - User deletes their own account
    public void deleteAccount(Long id) {

        // Delete user account and check result
        if (!userRepository.deleteById(id))
            throw new IllegalArgumentException("User not found.");
    }

    // DELETE - Admin deletes a user account
    public void deleteUserByAdmin(Long id) {

        // Delete selected user account
        if (!userRepository.deleteById(id))
            throw new IllegalArgumentException("User not found.");
    }

    // Helper - Normalize role string
    private String normalizeRole(String role) {

        // Default role if null
        if (role == null)
            return "BUYER";

        // Convert role to uppercase and match valid roles
        return switch (role.trim().toUpperCase()) {

            // Valid seller role
            case "SELLER" -> "SELLER";

            // Valid dealer role
            case "DEALER" -> "DEALER";

            // Default role
            default -> "BUYER";
        };
    }
}