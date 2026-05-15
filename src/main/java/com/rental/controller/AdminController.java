package com.rental.controller;

import com.rental.model.Admin;
import com.rental.model.Seller;
import com.rental.model.User;
import com.rental.model.Vehicle;
import com.rental.service.AdminService;
import com.rental.service.ReviewService;
import com.rental.service.SellerService;
import com.rental.service.TransactionService;
import com.rental.service.UserService;
import com.rental.service.VehicleService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Component 04: Admin Management Controller
// Full CRUD for admins + cross-module admin actions (approve sellers, manage vehicles, users, reviews)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String SESSION_ADMIN_ID = "AUTH_ADMIN_ID";
    private static final String SESSION_ROLE = "AUTH_ROLE";

    private final AdminService adminService;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final ReviewService reviewService;
    private final SellerService sellerService;
    private final TransactionService transactionService;

    public AdminController(AdminService adminService, UserService userService,
                           VehicleService vehicleService, ReviewService reviewService,
                           SellerService sellerService, TransactionService transactionService) {
        this.adminService = adminService;
        this.userService = userService;
        this.vehicleService = vehicleService;
        this.reviewService = reviewService;
        this.sellerService = sellerService;
        this.transactionService = transactionService;
    }

    // ═══ ADMIN ACCOUNT CRUD ═══

    // CREATE - Register new admin (only SUPERADMIN or first-time setup)
    @PostMapping("/admins")
    public ResponseEntity<?> createAdmin(@RequestBody AdminRequest req, HttpSession session) {
        if (!isAdmin(session) && adminService.countAdmins() > 0) return forbidden();
        try {
            Admin admin = adminService.createAdmin(req.name(), req.email(), req.password(), req.role());
            logAction(session, "Created admin: " + admin.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Admin created.", "admin", adminView(admin)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - Get own admin account
    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        String adminId = (String) session.getAttribute(SESSION_ADMIN_ID);
        Admin a = adminService.findById(adminId).orElse(null);
        if (a == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found."));
        return ResponseEntity.ok(Map.of("admin", adminView(a)));
    }

    // UPDATE - Update own admin profile
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody AdminUpdateRequest req, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        String adminId = (String) session.getAttribute(SESSION_ADMIN_ID);
        try {
            Admin a = adminService.findById(adminId)
                    .orElseThrow(() -> new IllegalArgumentException("Admin not found."));
            Admin updated = adminService.updateAdmin(adminId, req.name(), req.email(), req.password(), a.getRole());
            logAction(session, "Updated own profile");
            return ResponseEntity.ok(Map.of("message", "Account updated.", "admin", adminView(updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - List all admins
    @GetMapping("/admins")
    public ResponseEntity<?> listAdmins(HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(Map.of("admins",
                adminService.getAllAdmins().stream().map(this::adminView).toList()));
    }

    // READ - Get admin by ID
    @GetMapping("/admins/{adminId}")
    public ResponseEntity<?> getAdmin(@PathVariable String adminId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        Admin a = adminService.findById(adminId).orElse(null);
        if (a == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found."));
        return ResponseEntity.ok(Map.of("admin", adminView(a)));
    }

    // UPDATE - Update any admin account (SUPERADMIN only)
    @PutMapping("/admins/{adminId}")
    public ResponseEntity<?> updateAdmin(@PathVariable String adminId,
                                         @RequestBody AdminRequest req, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            Admin updated = adminService.updateAdmin(adminId, req.name(), req.email(), req.password(), req.role());
            logAction(session, "Updated admin: " + adminId);
            return ResponseEntity.ok(Map.of("message", "Admin updated.", "admin", adminView(updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Delete admin account
    @DeleteMapping("/admins/{adminId}")
    public ResponseEntity<?> deleteAdmin(@PathVariable String adminId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            adminService.deleteAdmin(adminId);
            logAction(session, "Deleted admin: " + adminId);
            return ResponseEntity.ok(Map.of("message", "Admin deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ ADMIN: USER MANAGEMENT ═══

    // READ - Search/list all users
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestParam(required = false) String query, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(Map.of("users",
                userService.searchUsers(query).stream().map(this::userView).toList()));
    }

    // DELETE - Remove inactive or fraudulent user account
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            userService.deleteUserByAdmin(userId);
            logAction(session, "Deleted user: " + userId);
            return ResponseEntity.ok(Map.of("message", "User deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ ADMIN: VEHICLE MANAGEMENT ═══

    // READ - List all vehicles (any status)
    @GetMapping("/vehicles")
    public ResponseEntity<?> listVehicles(@RequestParam(required = false) String status,
                                           HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        var all = vehicleService.getAllVehicles();
        var filtered = (status == null || status.isBlank()) ? all :
                all.stream().filter(v -> status.equalsIgnoreCase(v.getStatus())).toList();
        return ResponseEntity.ok(Map.of("vehicles", filtered.stream().map(this::vehicleView).toList()));
    }

    // READ - List pending vehicles awaiting approval
    @GetMapping("/vehicles/pending")
    public ResponseEntity<?> pendingVehicles(HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(Map.of("vehicles",
                vehicleService.getPendingVehicles().stream().map(this::vehicleView).toList()));
    }

    // UPDATE - Approve vehicle listing
    @PostMapping("/vehicles/{vehicleId}/approve")
    public ResponseEntity<?> approveVehicle(@PathVariable Long vehicleId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            Vehicle v = vehicleService.approveVehicle(vehicleId);
            logAction(session, "Approved vehicle: " + vehicleId);
            return ResponseEntity.ok(Map.of("message", "Vehicle approved.", "vehicle", vehicleView(v)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // UPDATE - Reject vehicle listing
    @PostMapping("/vehicles/{vehicleId}/reject")
    public ResponseEntity<?> rejectVehicle(@PathVariable Long vehicleId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            Vehicle v = vehicleService.rejectVehicle(vehicleId);
            logAction(session, "Rejected vehicle: " + vehicleId);
            return ResponseEntity.ok(Map.of("message", "Vehicle rejected.", "vehicle", vehicleView(v)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Remove sold or expired vehicle listing
    @DeleteMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long vehicleId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            vehicleService.deleteVehicleByAdmin(vehicleId);
            logAction(session, "Deleted vehicle: " + vehicleId);
            return ResponseEntity.ok(Map.of("message", "Vehicle deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ ADMIN: SELLER MANAGEMENT ═══

    // READ - List all sellers (with optional filter)
    @GetMapping("/sellers")
    public ResponseEntity<?> listSellers(@RequestParam(required = false) String status,
                                          HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        var all = sellerService.getAllSellers();
        if ("pending".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(Map.of("sellers",
                    sellerService.getPendingSellers().stream().map(this::sellerView).toList()));
        }
        return ResponseEntity.ok(Map.of("sellers", all.stream().map(this::sellerView).toList()));
    }

    // UPDATE - Approve seller account
    @PostMapping("/sellers/{sellerId}/approve")
    public ResponseEntity<?> approveSeller(@PathVariable Long sellerId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            Seller s = sellerService.approveSeller(sellerId);
            logAction(session, "Approved seller: " + sellerId);
            return ResponseEntity.ok(Map.of("message", "Seller approved.", "seller", sellerView(s)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // UPDATE - Reject/suspend seller account
    @PostMapping("/sellers/{sellerId}/reject")
    public ResponseEntity<?> rejectSeller(@PathVariable Long sellerId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            Seller s = sellerService.rejectSeller(sellerId);
            logAction(session, "Rejected seller: " + sellerId);
            return ResponseEntity.ok(Map.of("message", "Seller rejected.", "seller", sellerView(s)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Remove inactive or fraudulent seller
    @DeleteMapping("/sellers/{sellerId}")
    public ResponseEntity<?> deleteSeller(@PathVariable Long sellerId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            sellerService.deleteSeller(sellerId);
            logAction(session, "Deleted seller: " + sellerId);
            return ResponseEntity.ok(Map.of("message", "Seller deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ ADMIN: REVIEW MANAGEMENT ═══

    // READ - List all reviews (with optional query)
    @GetMapping("/reviews")
    public ResponseEntity<?> listReviews(@RequestParam(required = false) String query, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        var reviews = reviewService.getAllReviews().stream()
                .filter(r -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.toLowerCase();
                    return (r.getContent() != null && r.getContent().toLowerCase().contains(q))
                            || String.valueOf(r.getRating()).contains(q);
                }).toList();
        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    // DELETE - Remove inappropriate review
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long reviewId, HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        try {
            reviewService.deleteReviewByAdmin(reviewId);
            logAction(session, "Deleted review: " + reviewId);
            return ResponseEntity.ok(Map.of("message", "Review deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ ADMIN: ACTIVITY LOG & REPORT ═══

    // READ - View admin activity logs
    @GetMapping("/activity-log")
    public ResponseEntity<?> getActivityLog(HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        return ResponseEntity.ok(Map.of("logs", adminService.getActivityLog()));
    }

    // READ - Generate platform summary report
    @GetMapping("/report")
    public ResponseEntity<?> getReport(HttpSession session) {
        if (!isAdmin(session)) return forbidden();
        long users = userService.searchUsers(null).size();
        long sellers = sellerService.getAllSellers().size();
        long vehicles = vehicleService.getAllVehicles().size();
        long transactions = transactionService.findAll().size();
        AdminService.AdminReport report = adminService.generateReport(users, sellers, vehicles, transactions);
        logAction(session, "Generated platform report");
        return ResponseEntity.ok(Map.of(
                "report", Map.of(
                        "totalUsers", report.totalUsers(),
                        "totalSellers", report.totalSellers(),
                        "totalVehicles", report.totalVehicles(),
                        "totalTransactions", report.totalTransactions(),
                        "totalAdmins", report.totalAdmins(),
                        "activityLogEntries", report.logEntries()
                )));
    }

    // ═══ Helpers ═══

    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute(SESSION_ROLE);
        return "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
    }

    private void logAction(HttpSession session, String action) {
        String adminId = (String) session.getAttribute(SESSION_ADMIN_ID);
        if (adminId != null) adminService.logAction(adminId, action);
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Admin access required."));
    }

    private Map<String, Object> adminView(Admin a) {
        return Map.of("adminId", a.getAdminId() == null ? "" : a.getAdminId(),
                "name", a.getName() == null ? "" : a.getName(),
                "email", a.getEmail() == null ? "" : a.getEmail(),
                "role", a.getRole() == null ? "" : a.getRole());
    }

    private Map<String, Object> userView(User u) {
        return Map.of("id", u.getId(), "name", u.getName() == null ? "" : u.getName(),
                "email", u.getEmail() == null ? "" : u.getEmail(),
                "phone", u.getPhone() == null ? "" : u.getPhone(),
                "role", u.getRole() == null ? "" : u.getRole());
    }

    private Map<String, Object> vehicleView(Vehicle v) {
        String img = (v.getImages() == null || v.getImages().isEmpty()) ? "" : v.getImages().get(0);
        return Map.of("id", v.getId(), "brand", v.getBrand() == null ? "" : v.getBrand(),
                "model", v.getModel() == null ? "" : v.getModel(),
                "price", v.getPrice(), "status", v.getStatus() == null ? "" : v.getStatus(),
                "imageUrl", img, "sellerId", v.getSellerId(),
                "type", v.getType() == null ? "used" : v.getType());
    }

    private Map<String, Object> sellerView(Seller s) {
        return Map.of("id", s.getId(), "name", s.getName() == null ? "" : s.getName(),
                "email", s.getEmail() == null ? "" : s.getEmail(),
                "type", s.getType() == null ? "" : s.getType(),
                "location", s.getLocation() == null ? "" : s.getLocation(),
                "approved", s.isApproved());
    }

    public record AdminRequest(String name, String email, String password, String role) {}
    public record AdminUpdateRequest(String name, String email, String password) {}
}
