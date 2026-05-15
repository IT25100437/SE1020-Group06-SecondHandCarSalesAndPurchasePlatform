package com.rental.controller;
//done
import com.rental.model.Admin;
import com.rental.model.Seller;
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


        // ── Session attribute key constants ──────────────────────────────────────
        // Key used to store the logged-in user's ID in the HTTP session
        private static final String SESSION_USER_ID = "AUTH_USER_ID";
        // Key used to store the logged-in admin's ID in the HTTP session
        private static final String SESSION_ADMIN_ID = "AUTH_ADMIN_ID";
        // Key used to store the logged-in user's role (BUYER, SELLER, ADMIN, etc.)
        private static final String SESSION_ROLE = "AUTH_ROLE";

        // ── Service dependencies (injected via constructor) ───────────────────────
        private final UserService userService;     // Business logic for buyers/users
        private final AdminService adminService;   // Business logic for admins
        private final SellerService sellerService; // Business logic for sellers/dealers

        // Constructor-based dependency injection (preferred over @Autowired on fields)
        // Spring automatically provides the service beans when creating this controller
        public UserController(UserService userService, AdminService adminService, SellerService sellerService) {
            this.userService = userService;
            this.adminService = adminService;
            this.sellerService = sellerService;
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: POST /api/auth/register
        // PURPOSE : Register a new account (buyer, seller, or dealer)
        // NOTE    : Sellers/Dealers are auto-approved on registration.
        //           Only vehicle LISTINGS need admin approval, not the accounts.
        // ─────────────────────────────────────────────────────────────────────────
        @PostMapping("/auth/register")
        public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpSession session) {
            // @RequestBody maps the incoming JSON payload to a RegisterRequest record
            // HttpSession is injected by Spring to allow storing session data after registration
            try {
                // Default role to "BUYER" if none is provided; otherwise normalize to uppercase
                String role = req.role() == null ? "BUYER" : req.role().toUpperCase();

                // ── Branch: Registering as SELLER or DEALER ───────────────────────
                if ("SELLER".equals(role) || "DEALER".equals(role)) {
                    // Create a new Seller entity and populate it with request data
                    Seller seller = new Seller();
                    seller.setName(req.name());         // Full name of the seller
                    seller.setEmail(req.email());        // Email used for login
                    seller.setContact(req.phone());      // Contact phone number
                    seller.setPassword(req.password());  // Plain-text password (should be hashed in production)
                    seller.setLocation(req.address());   // Seller's physical location
                    // Seller type: "dealer" for DEALER role, "individual" for SELLER role
                    seller.setType("DEALER".equals(role) ? "dealer" : "individual");
                    // Auto-approve seller accounts — only car listings need admin review
                    seller.setApproved(true);

                    // Persist the seller in the database via the service layer
                    Seller created = sellerService.registerSeller(seller);

                    // Store the new seller's ID and role in the session (logs them in immediately)
                    session.setAttribute(SESSION_USER_ID, created.getId());
                    session.setAttribute(SESSION_ROLE, role);

                    // Return 201 Created with seller details and redirect instructions for the frontend
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "message", "Account created successfully! You can now list vehicles.",
                            "role", role,
                            // Return only safe, non-sensitive seller fields
                            "seller", Map.of("id", created.getId(), "name", created.getName(),
                                    "email", created.getEmail(), "type", created.getType()),
                            // Tells the frontend which page to navigate to after registration
                            "redirectPage", "seller-dashboard.html"));
                }

                // ── Branch: Registering as BUYER (default) ────────────────────────
                // Delegate account creation to UserService
                User user = userService.register(req.name(), req.email(), req.phone(),
                        req.address(), req.password(), role);

                // Log the buyer in immediately by storing their session data
                session.setAttribute(SESSION_USER_ID, user.getId());
                session.setAttribute(SESSION_ROLE, role);

                // Return 201 Created with safe user data and redirect page
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "message", "Account created successfully.",
                        "role", role,
                        "user", publicUser(user), // publicUser() strips sensitive fields like password
                        "redirectPage", "account.html"));

            } catch (IllegalArgumentException e) {
                // Triggered when the service layer detects invalid input (e.g., duplicate email)
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: POST /api/auth/login
        // PURPOSE : Authenticate a user and establish a session
        //           Tries admin → seller → buyer in order
        // ─────────────────────────────────────────────────────────────────────────
        @PostMapping("/auth/login")
        public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
            try {
                // ── Step 1: Attempt admin authentication first ────────────────────
                try {
                    // adminService.authenticate() throws an exception if credentials are wrong
                    Admin admin = adminService.authenticate(req.email(), req.password());

                    // Store admin ID and role in session
                    session.setAttribute(SESSION_ADMIN_ID, admin.getAdminId());
                    session.setAttribute(SESSION_ROLE, admin.getRole());

                    // Return success with admin data and redirect to admin dashboard
                    return ResponseEntity.ok(Map.of(
                            "message", "Login successful.", "role", admin.getRole(),
                            "admin", Map.of("adminId", admin.getAdminId(), "name", admin.getName(),
                                    "email", admin.getEmail(), "role", admin.getRole()),
                            "redirectPage", "admin-dashboard.html"));
                } catch (Exception ignored) {
                    // Not an admin — silently continue to next check
                }

                // ── Step 2: Attempt seller login ──────────────────────────────────
                // Look up seller by email (returns Optional<Seller>)
                var sellerOpt = sellerService.findByEmail(req.email());
                if (sellerOpt.isPresent()) {
                    Seller s = sellerOpt.get();
                    // Manually compare password (no approval check — all sellers can log in freely)
                    if (s.getPassword() != null && s.getPassword().equals(req.password())) {
                        // Store seller's ID and role in session
                        session.setAttribute(SESSION_USER_ID, s.getId());
                        session.setAttribute(SESSION_ROLE, "SELLER");

                        // Return success with seller data and redirect to seller dashboard
                        return ResponseEntity.ok(Map.of(
                                "message", "Login successful.", "role", "SELLER",
                                "seller", Map.of("id", s.getId(), "name", s.getName(),
                                        "email", s.getEmail(), "type", s.getType()),
                                "redirectPage", "seller-dashboard.html"));
                    }
                }

                // ── Step 3: Attempt buyer login ───────────────────────────────────
                // userService.login() throws an exception if credentials are invalid
                User user = userService.login(req.email(), req.password());

                // Store buyer's ID and role in session
                session.setAttribute(SESSION_USER_ID, user.getId());
                session.setAttribute(SESSION_ROLE, user.getRole());

                // Return success with safe user data and redirect to account page
                return ResponseEntity.ok(Map.of(
                        "message", "Login successful.", "role", user.getRole(),
                        "user", publicUser(user),
                        "redirectPage", "account.html"));

            } catch (Exception e) {
                // All three authentication attempts failed — return 401 Unauthorized
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid email or password."));
            }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: GET /api/auth/status
        // PURPOSE : Check if the current session has an authenticated user
        //           Used by the frontend to decide which UI to show
        // ─────────────────────────────────────────────────────────────────────────
        @GetMapping("/auth/status")
        public ResponseEntity<?> status(HttpSession session) {
            // Retrieve the role stored at login time
            String role = (String) session.getAttribute(SESSION_ROLE);

            // No role in session means the user is not logged in
            if (role == null)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("authenticated", false));

            // ── Admin/SuperAdmin session check ────────────────────────────────────
            if ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role)) {
                String adminId = (String) session.getAttribute(SESSION_ADMIN_ID);
                // Look up the admin in the database to confirm they still exist
                Admin a = adminService.findById(adminId).orElse(null);
                if (a == null) {
                    // Admin no longer exists — clear session and return unauthorized
                    session.invalidate();
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("authenticated", false));
                }
                // Admin is valid — return their info
                return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                        "admin", Map.of("adminId", a.getAdminId(), "name", a.getName(),
                                "email", a.getEmail(), "role", a.getRole())));
            }

            // ── Seller/Dealer session check ───────────────────────────────────────
            // Try to retrieve the user ID from the session (could be Long or Integer)
            Long userId = getSessionUserId(session);
            if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false));

            if ("SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role)) {
                // Verify seller still exists in the database
                Seller s = sellerService.findById(userId).orElse(null);
                if (s == null) {
                    // Seller account deleted — clear session and return unauthorized
                    session.invalidate();
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("authenticated", false));
                }
                // Seller is valid — return their info
                return ResponseEntity.ok(Map.of("authenticated", true, "role", role,
                        "seller", Map.of("id", s.getId(), "name", s.getName(),
                                "email", s.getEmail(), "type", s.getType())));
            }

            // ── Buyer session check ───────────────────────────────────────────────
            // Verify buyer still exists in the database
            User u = userService.findById(userId).orElse(null);
            if (u == null) {
                // Buyer account deleted — clear session and return unauthorized
                session.invalidate();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("authenticated", false));
            }
            // Buyer is valid — return their public info
            return ResponseEntity.ok(Map.of("authenticated", true, "role", role, "user", publicUser(u)));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: GET /api/account/me
        // PURPOSE : Retrieve the currently logged-in buyer's profile
        // ─────────────────────────────────────────────────────────────────────────
        @GetMapping("/account/me")
        public ResponseEntity<?> getMe(HttpSession session) {
            // Ensure the user is logged in by checking the session for a user ID
            Long userId = getSessionUserId(session);
            if (userId == null) return unauthorized(); // No session — return 401

            // Fetch the user from the database
            User u = userService.findById(userId).orElse(null);
            if (u == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found.")); // Account doesn't exist — 404

            // Return safe, public user data
            return ResponseEntity.ok(Map.of("user", publicUser(u)));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: PUT /api/account/me
        // PURPOSE : Update the currently logged-in buyer's profile information
        // ─────────────────────────────────────────────────────────────────────────
        @PutMapping("/account/me")
        public ResponseEntity<?> updateMe(@RequestBody UpdateRequest req, HttpSession session) {
            // Ensure the user is logged in
            Long userId = getSessionUserId(session);
            if (userId == null) return unauthorized(); // Not authenticated — return 401

            try {
                // Delegate update logic to the service layer
                User user = userService.updateProfile(userId, req.name(), req.email(),
                        req.phone(), req.address(), req.password());
                // Return updated profile data
                return ResponseEntity.ok(Map.of("message", "Account updated.", "user", publicUser(user)));
            } catch (IllegalArgumentException e) {
                // Invalid input (e.g., email already taken by another account)
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: DELETE /api/account/me
        // PURPOSE : Permanently delete the currently logged-in buyer's account
        // ─────────────────────────────────────────────────────────────────────────
        @DeleteMapping("/account/me")
        public ResponseEntity<?> deleteMe(HttpSession session) {
            // Ensure the user is logged in
            Long userId = getSessionUserId(session);
            if (userId == null) return unauthorized(); // Not authenticated — return 401

            try {
                // Delete the account from the database
                userService.deleteAccount(userId);
                // Invalidate the session — the user is now fully logged out
                session.invalidate();
                return ResponseEntity.ok(Map.of("message", "Account deleted."));
            } catch (IllegalArgumentException e) {
                // Account not found or deletion not allowed
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: GET /api/users/public
        // PURPOSE : Public user directory — returns name, role, and location only
        //           No authentication required; sensitive data (email, password) excluded
        // PARAMS  : query (optional) — search by name or keyword
        //           role  (optional) — filter results by role (e.g., BUYER, SELLER)
        // ─────────────────────────────────────────────────────────────────────────
        @GetMapping("/users/public")
        public ResponseEntity<?> publicUsers(
                @RequestParam(required = false) String query, // Optional search term (null if not provided)
                @RequestParam(required = false) String role   // Optional role filter (null if not provided)
        ) {
            return ResponseEntity.ok(Map.of("users",
                    userService.searchUsers(query).stream() // Fetch users matching the query
                            // If a role filter is provided, keep only users with that role
                            .filter(u -> role == null || role.isBlank() || role.equalsIgnoreCase(u.getRole()))
                            // Map each User entity to a safe public-facing map (no email, no password)
                            .map(u -> Map.of(
                                    "id",      u.getId(),
                                    "name",    u.getName()    == null ? "" : u.getName(),    // Null-safe
                                    "role",    u.getRole()    == null ? "BUYER" : u.getRole(), // Default to BUYER
                                    "phone",   u.getPhone()   == null ? "" : u.getPhone(),   // Null-safe
                                    "address", u.getAddress() == null ? "" : u.getAddress()  // Null-safe
                            )).toList())); // Collect stream results into a List
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ENDPOINT: POST /api/auth/logout
        // PURPOSE : Log out the current user by destroying their session
        // ─────────────────────────────────────────────────────────────────────────
        @PostMapping("/auth/logout")
        public ResponseEntity<?> logout(HttpSession session) {
            session.invalidate(); // Clears all session attributes and ends the session
            return ResponseEntity.ok(Map.of("message", "Logged out."));
        }

        // ═════════════════════════════════════════════════════════════════════════
        // HELPER METHODS
        // ═════════════════════════════════════════════════════════════════════════

        /**
         * Safely retrieves the user ID from the HTTP session.
         * The ID may be stored as Long or Integer depending on how it was set,
         * so both types are handled explicitly.
         */
        private Long getSessionUserId(HttpSession session) {
            Object raw = session.getAttribute(SESSION_USER_ID); // Retrieve raw value (type unknown)
            if (raw instanceof Long l)    return l;             // Already a Long — return directly
            if (raw instanceof Integer i) return i.longValue(); // Integer — widen to Long safely
            return null; // Not set or unexpected type — treat as unauthenticated
        }

        /**
         * Builds a standardized 401 Unauthorized response.
         * Used whenever a protected endpoint is accessed without a valid session.
         */
        private ResponseEntity<?> unauthorized() {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please login first."));
        }

        /**
         * Converts a User entity into a safe public Map for API responses.
         * Excludes sensitive fields like password.
         * Null values are replaced with empty strings to avoid NullPointerExceptions in the frontend.
         */
        private Map<String, Object> publicUser(User u) {
            return Map.of(
                    "id",      u.getId(),
                    "name",    u.getName()    == null ? "" : u.getName(),    // Null-safe name
                    "email",   u.getEmail()   == null ? "" : u.getEmail(),   // Null-safe email
                    "phone",   u.getPhone()   == null ? "" : u.getPhone(),   // Null-safe phone
                    "address", u.getAddress() == null ? "" : u.getAddress(), // Null-safe address
                    "role",    u.getRole()    == null ? "BUYER" : u.getRole() // Default role if missing
            );
        }

        // ═════════════════════════════════════════════════════════════════════════
        // REQUEST RECORD TYPES
        // Java records are immutable data carriers — auto-generate constructor,
        // getters, equals, hashCode, and toString.
        // Spring uses these as @RequestBody targets, mapping JSON fields by name.
        // ═════════════════════════════════════════════════════════════════════════

        // Used for POST /api/auth/register
        // Captures all fields a new user might submit during registration
        public record RegisterRequest(String name, String email, String phone,
                                      String address, String password, String role) {}

        // Used for POST /api/auth/login
        // Only email and password are needed to authenticate
        public record LoginRequest(String email, String password) {}

        // Used for PUT /api/account/me
        // All fields are optional — only provided fields should be updated (handled in service layer)
        public record UpdateRequest(String name, String email, String phone,
                                    String address, String password) {}
    }
}