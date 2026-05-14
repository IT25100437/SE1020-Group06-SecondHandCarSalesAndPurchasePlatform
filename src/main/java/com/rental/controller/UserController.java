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


        // Key used to store the logged-in user's ID in the HTTP session
        private static final String SESSION_USER_ID = "AUTH_USER_ID";
        // Key used to store the logged-in admin's ID in the HTTP session
        private static final String SESSION_ADMIN_ID = "AUTH_ADMIN_ID";
        // Key used to store the logged-in user's role (BUYER, SELLER, ADMIN, etc.) in the HTTP session
        private static final String SESSION_ROLE = "AUTH_ROLE";

        private final UserService userService;     // Handles buyer/user business logic
        private final AdminService adminService;   // Handles admin business logic
        private final SellerService sellerService; // Handles seller/dealer business logic

        // Constructor-based dependency injection — Spring automatically provides these service beans
        public UserController(UserService userService, AdminService adminService, SellerService sellerService) {
            this.userService = userService;
            this.adminService = adminService;
            this.sellerService = sellerService;
        }

        // CREATE - Register new user account
        // NOTE: Sellers/Dealers are auto-approved on registration.
        // Only vehicle LISTINGS require admin approval (not accounts).
        @PostMapping("/auth/register") // Maps HTTP POST /api/auth/register to this method
        public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpSession session) {
            // @RequestBody maps incoming JSON payload to RegisterRequest record
            // HttpSession allows storing session data immediately after registration (auto-login)
            try {
                // Default role to "BUYER" if not provided; otherwise normalize to uppercase
                String role = req.role() == null ? "BUYER" : req.role().toUpperCase();

                // ── Branch: Registering as SELLER or DEALER ───────────────────────
                if ("SELLER".equals(role) || "DEALER".equals(role)) {
                    Seller seller = new Seller(); // Create a new Seller entity
                    seller.setName(req.name());         // Full name from registration form
                    seller.setEmail(req.email());        // Email used as login identifier
                    seller.setContact(req.phone());      // Contact phone number
                    seller.setPassword(req.password());  // Password (should be hashed in production)
                    seller.setLocation(req.address());   // Physical location of the seller
                    // Set seller type: "dealer" for DEALER role, "individual" for SELLER role
                    seller.setType("DEALER".equals(role) ? "dealer" : "individual");
                    seller.setApproved(true); // auto-approve account; only car listings need admin approval
                    Seller created = sellerService.registerSeller(seller); // Persist seller to database
                    session.setAttribute(SESSION_USER_ID, created.getId()); // Log seller in immediately via session
                    session.setAttribute(SESSION_ROLE, role);               // Store role in session
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "message", "Account created successfully! You can now list vehicles.",
                            "role", role,
                            // Return only safe, non-sensitive seller fields (never expose password)
                            "seller", Map.of("id", created.getId(), "name", created.getName(),
                                    "email", created.getEmail(), "type", created.getType()),
                            "redirectPage", "seller-dashboard.html")); // Tell frontend where to navigate
                }

                // ── Branch: Registering as BUYER (default role) ───────────────────
                User user = userService.register(req.name(), req.email(), req.phone(),
                        req.address(), req.password(), role); // Delegate creation to service layer
                session.setAttribute(SESSION_USER_ID, user.getId()); // Log buyer in immediately via session
                session.setAttribute(SESSION_ROLE, role);             // Store role in session
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "message", "Account created successfully.",
                        "role", role,
                        "user", publicUser(user), // publicUser() strips sensitive fields like password
                        "redirectPage", "account.html")); // Tell frontend where to navigate
            } catch (IllegalArgumentException e) {
                // Triggered when service detects invalid input (e.g., duplicate email)
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // READ - Login
        @PostMapping("/auth/login") // Maps HTTP POST /api/auth/login to this method
        public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
            try {
                // 1. Try admin login first
                try {
                    // authenticate() throws an exception if credentials are wrong — caught silently below
                    Admin admin = adminService.authenticate(req.email(), req.password());
                    session.setAttribute(SESSION_ADMIN_ID, admin.getAdminId()); // Store admin ID in session
                    session.setAttribute(SESSION_ROLE, admin.getRole());         // Store admin role in session
                    return ResponseEntity.ok(Map.of(
                            "message", "Login successful.", "role", admin.getRole(),
                            // Return safe admin details (no password)
                            "admin", Map.of("adminId", admin.getAdminId(), "name", admin.getName(),
                                    "email", admin.getEmail(), "role", admin.getRole()),
                            "redirectPage", "admin-dashboard.html")); // Tell frontend to go to admin dashboard
                } catch (Exception ignored) {} // Not an admin — silently fall through to next check

                // 2. Try seller login — NO approval check, all sellers can log in freely
                var sellerOpt = sellerService.findByEmail(req.email()); // Look up seller by email
                if (sellerOpt.isPresent()) {
                    Seller s = sellerOpt.get();
                    // Manually compare password (plain-text comparison — use BCrypt in production)
                    if (s.getPassword() != null && s.getPassword().equals(req.password())) {
                        session.setAttribute(SESSION_USER_ID, s.getId()); // Store seller ID in session
                        session.setAttribute(SESSION_ROLE, "SELLER");      // Store seller role in session
                        return ResponseEntity.ok(Map.of(
                                "message", "Login successful.", "role", "SELLER",
                                // Return safe seller details (no password)
                                "seller", Map.of("id", s.getId(), "name", s.getName(),
                                        "email", s.getEmail(), "type", s.getType()),
                                "redirectPage", "seller-dashboard.html")); // Tell frontend to go to seller dashboard
                    }
                }

                // 3. Try buyer login
                // userService.login() throws an exception if credentials are invalid
                User user = userService.login(req.email(), req.password());
                session.setAttribute(SESSION_USER_ID, user.getId()); // Store buyer ID in session
                session.setAttribute(SESSION_ROLE, user.getRole());   // Store buyer role in session
                return ResponseEntity.ok(Map.of(
                        "message", "Login successful.", "role", user.getRole(),
                        "user", publicUser(user), // Return safe buyer details (no password)
                        "redirectPage", "account.html")); // Tell frontend to go to account page

            } catch (Exception e) {
                // All three login attempts failed — return 401 Unauthorized
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid email or password."));
            }
        }

        // READ - Check auth status
        @GetMapping("/auth/status") // Maps HTTP GET /api/auth/status to this method
        public ResponseEntity<?> status(HttpSession session) {
            String role = (String) session.getAttribute(SESSION_ROLE); // Retrieve role stored at login time
            if (role == null)
                // No role in session means the user is not logged in
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("authenticated", false));

            // ── Admin / SuperAdmin session validation ─────────────────────────────
            if ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role)) {
                String adminId = (String) session.getAttribute(SESSION_ADMIN_ID); // Retrieve admin ID from session
                Admin a = adminService.findById(adminId).orElse(null); // Verify admin still exists in DB
                if (a == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("authenticated", false)); } // Admin deleted — clear session, return 401
                return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                        // Return safe admin info to the frontend
                        "admin", Map.of("adminId", a.getAdminId(), "name", a.getName(),
                                "email", a.getEmail(), "role", a.getRole())));
            }

            // Retrieve user ID from session (handles both Long and Integer types)
            Long userId = getSessionUserId(session);
            if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false)); // No valid user ID — return 401

            // ── Seller / Dealer session validation ────────────────────────────────
            if ("SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role)) {
                Seller s = sellerService.findById(userId).orElse(null); // Verify seller still exists in DB
                if (s == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("authenticated", false)); } // Seller deleted — clear session, return 401
                return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                        // Return safe seller info to the frontend
                        "seller", Map.of("id", s.getId(), "name", s.getName(),
                                "email", s.getEmail(), "type", s.getType())));
            }

            // ── Buyer session validation ──────────────────────────────────────────
            User u = userService.findById(userId).orElse(null); // Verify buyer still exists in DB
            if (u == null) { session.invalidate(); return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false)); } // Buyer deleted — clear session, return 401
            return ResponseEntity.ok(Map.of("authenticated", true, "role", role, "user", publicUser(u)));
        }

        // READ - Get own account
        @GetMapping("/account/me") // Maps HTTP GET /api/account/me to this method
        public ResponseEntity<?> getMe(HttpSession session) {
            Long userId = getSessionUserId(session); // Check if user is logged in via session
            if (userId == null) return unauthorized(); // No session ID — return 401
            User u = userService.findById(userId).orElse(null); // Fetch user from database
            if (u == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found.")); // Account doesn't exist — return 404
            return ResponseEntity.ok(Map.of("user", publicUser(u))); // Return safe profile data
        }

        // UPDATE - Update own profile
        @PutMapping("/account/me") // Maps HTTP PUT /api/account/me to this method
        public ResponseEntity<?> updateMe(@RequestBody UpdateRequest req, HttpSession session) {
            Long userId = getSessionUserId(session); // Check if user is logged in via session
            if (userId == null) return unauthorized(); // No session ID — return 401
            try {
                // Delegate update logic to service layer (service handles partial updates)
                User user = userService.updateProfile(userId, req.name(), req.email(),
                        req.phone(), req.address(), req.password());
                return ResponseEntity.ok(Map.of("message", "Account updated.", "user", publicUser(user))); // Return updated safe profile
            } catch (IllegalArgumentException e) {
                // Invalid input — e.g., email already taken by another account
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // DELETE - Delete own account
        @DeleteMapping("/account/me") // Maps HTTP DELETE /api/account/me to this method
        public ResponseEntity<?> deleteMe(HttpSession session) {
            Long userId = getSessionUserId(session); // Check if user is logged in via session
            if (userId == null) return unauthorized(); // No session ID — return 401
            try {
                userService.deleteAccount(userId); // Permanently delete account from database
                session.invalidate(); // Clear session — user is now fully logged out
                return ResponseEntity.ok(Map.of("message", "Account deleted."));
            } catch (IllegalArgumentException e) {
                // Account not found or deletion not permitted
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // READ - Public user directory (name, role, location only - no sensitive data)
        @GetMapping("/users/public") // Maps HTTP GET /api/users/public to this method
        public ResponseEntity<?> publicUsers(@RequestParam(required = false) String query, // Optional search keyword (null if omitted)
                                             @RequestParam(required = false) String role) { // Optional role filter (null if omitted)
            return ResponseEntity.ok(Map.of("users",
                    userService.searchUsers(query).stream() // Fetch all users matching the search query
                            // If role filter is provided, keep only users with that matching role
                            .filter(u -> role == null || role.isBlank() || role.equalsIgnoreCase(u.getRole()))
                            // Map each User entity to a safe public map — no email, no password
                            .map(u -> Map.of(
                                    "id",    u.getId(),
                                    "name",  u.getName()    == null ? "" : u.getName(),      // Null-safe: default to empty string
                                    "role",  u.getRole()    == null ? "BUYER" : u.getRole(), // Null-safe: default to BUYER
                                    "phone", u.getPhone()   == null ? "" : u.getPhone(),     // Null-safe: default to empty string
                                    "address", u.getAddress() == null ? "" : u.getAddress()  // Null-safe: default to empty string
                            )).toList())); // Collect stream results into a List
        }

        // Logout
        @PostMapping("/auth/logout") // Maps HTTP POST /api/auth/logout to this method
        public ResponseEntity<?> logout(HttpSession session) {
            session.invalidate(); // Destroys the session and clears all stored attributes
            return ResponseEntity.ok(Map.of("message", "Logged out."));
        }

        // ═══ Helpers ═══

        // Safely retrieves the user ID from the HTTP session
        // Handles both Long and Integer types since session storage can return either
        private Long getSessionUserId(HttpSession session) {
            Object raw = session.getAttribute(SESSION_USER_ID); // Retrieve raw value (type unknown at compile time)
            if (raw instanceof Long l) return l;             // Already a Long — return directly
            if (raw instanceof Integer i) return i.longValue(); // Integer — widen to Long safely
            return null; // Not set or unrecognized type — treat as unauthenticated
        }

        // Builds a standardized 401 Unauthorized response
        // Used by all protected endpoints when no valid session is found
        private ResponseEntity<?> unauthorized() {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please login first."));
        }

        // Converts a User entity to a safe public Map for API responses
        // Excludes sensitive fields (e.g., password); replaces nulls with empty strings
        private Map<String, Object> publicUser(User u) {
            return Map.of("id", u.getId(), "name", u.getName() == null ? "" : u.getName(),     // Null-safe name
                    "email", u.getEmail() == null ? "" : u.getEmail(),                          // Null-safe email
                    "phone", u.getPhone() == null ? "" : u.getPhone(),                          // Null-safe phone
                    "address", u.getAddress() == null ? "" : u.getAddress(),                    // Null-safe address
                    "role", u.getRole() == null ? "BUYER" : u.getRole());                       // Default role if missing
        }

        // ── Request Record Types ──────────────────────────────────────────────────
        // Java records are immutable data carriers — auto-generate constructor, getters,
        // equals, hashCode, and toString. Spring maps incoming JSON fields to these by name.

        public record RegisterRequest(String name, String email, String phone,
                                      String address, String password, String role) {} // All fields a new user submits during registration

        public record LoginRequest(String email, String password) {} // Only email + password needed to authenticate

        public record UpdateRequest(String name, String email, String phone,
                                    String address, String password) {} // All fields are optional — service handles partial updates
    }
}