package com.rental.service;

import com.rental.model.Admin;
import com.rental.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Component 04: Admin Management - Service layer
// OOP: Uses Admin model which extends User (Inheritance + Polymorphism)
@Service
public class AdminService {

    private final AdminRepository adminRepository;

    // In-memory activity log - unique Admin feature for viva demonstration
    private final List<String> activityLog = new ArrayList<>();
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    // CREATE - Register new admin account
    public Admin createAdmin(String name, String email, String password, String role) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required.");
        if (adminRepository.findByEmail(email).isPresent())
            throw new IllegalArgumentException("Email already registered.");

        Admin admin = new Admin();
        admin.setName(name.trim());
        admin.setEmail(email.trim().toLowerCase());
        admin.setPasswordHash(password);
        admin.setRole(role == null || role.isBlank() ? "ADMIN" : role.trim().toUpperCase());
        Admin saved = adminRepository.save(admin);
        log("CREATED admin account: [" + saved.getAdminId() + "] " + saved.getEmail());
        return saved;
    }

    // READ - Authenticate admin (login)
    public Admin authenticate(String email, String password) {
        if (email == null || password == null) throw new IllegalArgumentException("Invalid credentials.");
        Admin admin = adminRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials."));
        if (!admin.getPasswordHash().equals(password))
            throw new IllegalArgumentException("Invalid credentials.");
        log("LOGIN: admin [" + admin.getAdminId() + "] " + admin.getEmail());
        return admin;
    }

    // READ - Get all admin accounts
    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    // READ - Find by ID
    public Optional<Admin> findById(String adminId) {
        return adminRepository.findById(adminId);
    }

    // READ - Find by email
    public Optional<Admin> findByEmail(String email) {
        return adminRepository.findByEmail(email);
    }

    // UPDATE - Update admin profile or change permissions
    public Admin updateAdmin(String adminId, String name, String email, String password, String role) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found."));
        if (name != null && !name.isBlank()) admin.setName(name.trim());
        if (email != null && !email.isBlank()) {
            Optional<Admin> existing = adminRepository.findByEmail(email.trim().toLowerCase());
            if (existing.isPresent() && !existing.get().getAdminId().equalsIgnoreCase(adminId))
                throw new IllegalArgumentException("Email already in use by another admin.");
            admin.setEmail(email.trim().toLowerCase());
        }
        if (password != null && !password.isBlank()) admin.setPasswordHash(password);
        if (role != null && !role.isBlank()) admin.setRole(role.trim().toUpperCase());
        Admin updated = adminRepository.update(admin);
        log("UPDATED admin [" + adminId + "] profile/permissions");
        return updated;
    }

    // DELETE - Remove admin account (only SUPERADMIN can delete other admins)
    public void deleteAdmin(String adminId) {
        if (!adminRepository.deleteById(adminId))
            throw new IllegalArgumentException("Admin not found.");
        log("DELETED admin [" + adminId + "]");
    }

    // READ - Get activity log (unique Admin module feature)
    public List<String> getActivityLog() {
        return new ArrayList<>(activityLog);
    }

    // Called by other controllers to record admin actions into the log
    public void logAction(String adminId, String action) {
        log("[" + adminId + "] " + action);
    }

    // READ - Generate a platform summary report (unique Admin feature)
    public AdminReport generateReport(long totalUsers, long totalSellers,
                                       long totalVehicles, long totalTransactions) {
        log("REPORT generated - users:" + totalUsers + " sellers:" + totalSellers
                + " vehicles:" + totalVehicles + " transactions:" + totalTransactions);
        return new AdminReport(totalUsers, totalSellers, totalVehicles,
                totalTransactions, adminRepository.count(), activityLog.size());
    }

    public long countAdmins() {
        return adminRepository.count();
    }

    private void log(String msg) {
        String entry = "[" + LocalDateTime.now().format(LOG_FMT) + "] " + msg;
        activityLog.add(entry);
        if (activityLog.size() > 500) activityLog.remove(0);
    }

    // Encapsulation: report data bundled into a record
    public record AdminReport(long totalUsers, long totalSellers, long totalVehicles,
                               long totalTransactions, long totalAdmins, int logEntries) {}
}
