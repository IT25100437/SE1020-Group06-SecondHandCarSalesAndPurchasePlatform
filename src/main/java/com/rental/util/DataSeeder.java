package com.rental.util;

import com.rental.service.AdminService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Seeds a default admin account on first startup
@Component
public class DataSeeder implements ApplicationRunner {

    private final AdminService adminService;

    public DataSeeder(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Only create if no admins exist
        if (adminService.countAdmins() == 0) {
            try {
                adminService.createAdmin("Super Admin", "admin@carspot.lk", "admin123", "SUPERADMIN");
                System.out.println("==============================================");
                System.out.println("  Default admin created:");
                System.out.println("  Email:    admin@carspot.lk");
                System.out.println("  Password: admin123");
                System.out.println("==============================================");
            } catch (Exception e) {
                System.out.println("Admin seeding skipped: " + e.getMessage());
            }
        }
    }
}
