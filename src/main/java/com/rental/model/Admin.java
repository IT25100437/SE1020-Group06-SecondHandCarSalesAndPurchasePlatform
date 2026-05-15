package com.rental.model;

// Component 04: Admin Management
// Inheritance: Admin extends User — reuses User fields (name, email, passwordHash)
// Polymorphism: overrides getDashboardPage() and getRole()
// Encapsulation: adminId and role are private with getters/setters
public class Admin extends User {

    private String adminId;   // e.g. "A001"
    private String adminRole; // "ADMIN" or "SUPERADMIN" — stored separately to avoid clash with User.role

    public Admin() {}

    public Admin(String adminId, String name, String email, String passwordHash, String role) {
        super();
        this.adminId = adminId;
        this.setName(name);
        this.setEmail(email);
        this.setPasswordHash(passwordHash);
        this.adminRole = role;
    }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    // Polymorphism: Admin.getRole() returns ADMIN/SUPERADMIN, not BUYER/SELLER
    @Override
    public String getRole() { return adminRole; }

    @Override
    public void setRole(String role) { this.adminRole = role; }

    // Polymorphism: Admin goes to admin dashboard, not account.html
    @Override
    public String getDashboardPage() { return "admin-dashboard.html"; }

    // Abstraction: only admins can manage users/vehicles/reviews
    public boolean canManageUsers() {
        return "ADMIN".equalsIgnoreCase(adminRole) || "SUPERADMIN".equalsIgnoreCase(adminRole);
    }

    // Only SUPERADMIN can create other admins
    public boolean isSuperAdmin() {
        return "SUPERADMIN".equalsIgnoreCase(adminRole);
    }
}
