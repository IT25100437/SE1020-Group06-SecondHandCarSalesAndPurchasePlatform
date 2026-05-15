package com.rental.controller;

import com.rental.model.Admin;
import com.rental.model.Seller;
import com.rental.model.User;
import com.rental.service.AdminService;
import com.rental.service.SellerService;
import com.rental.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Component 01: User Management Controller
@RestController
@RequestMapping("/api")
public class UserController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ADMIN_ID = "AUTH_ADMIN_ID";
    private static final String SESSION_ROLE = "AUTH_ROLE";

    private final UserService userService;
    private final AdminService adminService;
    private final SellerService sellerService;

    public UserController(UserService userService, AdminService adminService, SellerService sellerService) {
        this.userService = userService;
        this.adminService = adminService;
        this.sellerService = sellerService;
    }

    // CREATE - Register new user account
    // NOTE: Sellers/Dealers are auto-approved on registration.
    // Only vehicle LISTINGS require admin approval (not accounts).
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpSession session) {
        try {
            String role = req.role() == null ? "BUYER" : req.role().toUpperCase();

            if ("SELLER".equals(role) || "DEALER".equals(role)) {
                Seller seller = new Seller();
                seller.setName(req.name());
                seller.setEmail(req.email());
                seller.setContact(req.phone());
                seller.setPassword(req.password());
                seller.setLocation(req.address());
                seller.setType("DEALER".equals(role) ? "dealer" : "individual");
                seller.setApproved(true); // auto-approve account; only car listings need admin approval
                Seller created = sellerService.registerSeller(seller);
                session.setAttribute(SESSION_USER_ID, created.getId());
                session.setAttribute(SESSION_ROLE, role);
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "message", "Account created successfully! You can now list vehicles.",
                        "role", role,
                        "seller", Map.of("id", created.getId(), "name", created.getName(),
                                "email", created.getEmail(), "type", created.getType()),
                        "redirectPage", "seller-dashboard.html"));
            }

            User user = userService.register(req.name(), req.email(), req.phone(),
                    req.address(), req.password(), role);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_ROLE, role);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Account created successfully.",
                    "role", role,
                    "user", publicUser(user),
                    "redirectPage", "account.html"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - Login
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
        try {
            // 1. Try admin login first
            try {
                Admin admin = adminService.authenticate(req.email(), req.password());
                session.setAttribute(SESSION_ADMIN_ID, admin.getAdminId());
                session.setAttribute(SESSION_ROLE, admin.getRole());
                return ResponseEntity.ok(Map.of(
                        "message", "Login successful.", "role", admin.getRole(),
                        "admin", Map.of("adminId", admin.getAdminId(), "name", admin.getName(),
                                "email", admin.getEmail(), "role", admin.getRole()),
                        "redirectPage", "admin-dashboard.html"));
            } catch (Exception ignored) {}

            // 2. Try seller login — NO approval check, all sellers can log in freely
            var sellerOpt = sellerService.findByEmail(req.email());
            if (sellerOpt.isPresent()) {
                Seller s = sellerOpt.get();
                if (s.getPassword() != null && s.getPassword().equals(req.password())) {
                    session.setAttribute(SESSION_USER_ID, s.getId());
                    session.setAttribute(SESSION_ROLE, "SELLER");
                    return ResponseEntity.ok(Map.of(
                            "message", "Login successful.", "role", "SELLER",
                            "seller", Map.of("id", s.getId(), "name", s.getName(),
                                    "email", s.getEmail(), "type", s.getType()),
                            "redirectPage", "seller-dashboard.html"));
                }
            }

            // 3. Try buyer login
            User user = userService.login(req.email(), req.password());
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_ROLE, user.getRole());
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful.", "role", user.getRole(),
                    "user", publicUser(user),
                    "redirectPage", "account.html"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));
        }
    }

    // READ - Check auth status
    @GetMapping("/auth/status")
    public ResponseEntity<?> status(HttpSession session) {
        String role = (String) session.getAttribute(SESSION_ROLE);
        if (role == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("authenticated", false));

        if ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role)) {
            String adminId = (String) session.getAttribute(SESSION_ADMIN_ID);
            Admin a = adminService.findById(adminId).orElse(null);
            if (a == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false)); }
            return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                    "admin", Map.of("adminId", a.getAdminId(), "name", a.getName(),
                            "email", a.getEmail(), "role", a.getRole())));
        }

        Long userId = getSessionUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false));

        if ("SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role)) {
            Seller s = sellerService.findById(userId).orElse(null);
            if (s == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false)); }
            return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                    "seller", Map.of("id", s.getId(), "name", s.getName(),
                            "email", s.getEmail(), "type", s.getType())));
        }

        User u = userService.findById(userId).orElse(null);
        if (u == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false)); }
        return ResponseEntity.ok(Map.of("authenticated", true, "role", role, "user", publicUser(u)));
    }

    // READ - Get own account
    @GetMapping("/account/me")
    public ResponseEntity<?> getMe(HttpSession session) {
        Long userId = getSessionUserId(session);
        if (userId == null) return unauthorized();
        User u = userService.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "User not found."));
        return ResponseEntity.ok(Map.of("user", publicUser(u)));
    }

    // UPDATE - Update own profile
    @PutMapping("/account/me")
    public ResponseEntity<?> updateMe(@RequestBody UpdateRequest req, HttpSession session) {
        Long userId = getSessionUserId(session);
        if (userId == null) return unauthorized();
        try {
            User user = userService.updateProfile(userId, req.name(), req.email(),
                    req.phone(), req.address(), req.password());
            return ResponseEntity.ok(Map.of("message", "Account updated.", "user", publicUser(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Delete own account
    @DeleteMapping("/account/me")
    public ResponseEntity<?> deleteMe(HttpSession session) {
        Long userId = getSessionUserId(session);
        if (userId == null) return unauthorized();
        try {
            userService.deleteAccount(userId);
            session.invalidate();
            return ResponseEntity.ok(Map.of("message", "Account deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Logout
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }

    // ═══ Helpers ═══
    private Long getSessionUserId(HttpSession session) {
        Object raw = session.getAttribute(SESSION_USER_ID);
        if (raw instanceof Long l) return l;
        if (raw instanceof Integer i) return i.longValue();
        return null;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Please login first."));
    }

    private Map<String, Object> publicUser(User u) {
        return Map.of("id", u.getId(), "name", u.getName() == null ? "" : u.getName(),
                "email", u.getEmail() == null ? "" : u.getEmail(),
                "phone", u.getPhone() == null ? "" : u.getPhone(),
                "address", u.getAddress() == null ? "" : u.getAddress(),
                "role", u.getRole() == null ? "BUYER" : u.getRole());
    }

    public record RegisterRequest(String name, String email, String phone,
                                  String address, String password, String role) {}
    public record LoginRequest(String email, String password) {}
    public record UpdateRequest(String name, String email, String phone,
                                String address, String password) {}
}
