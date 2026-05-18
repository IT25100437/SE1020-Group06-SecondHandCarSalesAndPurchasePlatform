package com.rental.controller;

import com.rental.model.Seller;
import com.rental.service.SellerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Component 05: Seller & Dealer Management Controller
// Full CRUD: register, view, search, update profile/password, delete
@RestController
@RequestMapping("/api/sellers")
public class SellerController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ROLE = "AUTH_ROLE";

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    // CREATE - Register a new seller or dealer account
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Seller seller) {
        try {
            Seller created = sellerService.registerSeller(seller);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Registered successfully. Awaiting admin approval.",
                    "seller", sellerView(created)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - Get seller by ID (public)
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Seller s = sellerService.findById(id).orElse(null);
        if (s == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Seller not found."));
        return ResponseEntity.ok(Map.of("seller", sellerView(s)));
    }

    // READ - Get logged-in seller's own profile
    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpSession session) {
        Long userId = uid(session);
        if (userId == null || !isSeller(session)) return unauth();
        Seller s = sellerService.findById(userId).orElse(null);
        if (s == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Seller not found."));
        return ResponseEntity.ok(Map.of("seller", sellerView(s)));
    }

    // READ - Search sellers by name, location, or both (public)
    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String location) {
        return ResponseEntity.ok(Map.of("sellers",
                sellerService.searchSellers(name, location).stream().map(this::sellerView).toList()));
    }

    // READ - Get only approved sellers (public listing)
    @GetMapping("/approved")
    public ResponseEntity<?> approved() {
        return ResponseEntity.ok(Map.of("sellers",
                sellerService.getApprovedSellers().stream().map(this::sellerView).toList()));
    }

    // UPDATE - Seller updates their own contact info, location, or profile image
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody SellerUpdateRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null || !isSeller(session)) return unauth();
        try {
            Seller updated = sellerService.updateSeller(userId, req.name(), req.contact(),
                    req.location(), req.image(), req.email());
            return ResponseEntity.ok(Map.of("message", "Profile updated.", "seller", sellerView(updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // UPDATE - Seller changes their password
    @PutMapping("/me/password")
    public ResponseEntity<?> updatePassword(@RequestBody PasswordUpdateRequest req,
                                            HttpSession session) {
        Long userId = uid(session);
        if (userId == null || !isSeller(session)) return unauth();
        try {
            sellerService.updatePassword(userId, req.oldPassword(), req.newPassword());
            return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Seller deletes their own account
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMe(HttpSession session) {
        Long userId = uid(session);
        if (userId == null || !isSeller(session)) return unauth();
        try {
            sellerService.deleteSeller(userId);
            session.invalidate();
            return ResponseEntity.ok(Map.of("message", "Account deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ Helpers ═══

    private Map<String, Object> sellerView(Seller s) {
        return Map.of(
                "id", s.getId(),
                "name", s.getName() == null ? "" : s.getName(),
                "email", s.getEmail() == null ? "" : s.getEmail(),
                "contact", s.getContact() == null ? "" : s.getContact(),
                "location", s.getLocation() == null ? "" : s.getLocation(),
                "type", s.getType() == null ? "individual" : s.getType(),
                "image", s.getImage() == null ? "" : s.getImage(),
                "approved", s.isApproved(),
                "sellerTypeBadge", s.getSellerTypeBadge());
    }

    private Long uid(HttpSession s) {
        Object r = s.getAttribute(SESSION_USER_ID);
        if (r instanceof Long l) return l;
        if (r instanceof Integer i) return i.longValue();
        return null;
    }

    private boolean isSeller(HttpSession s) {
        String role = (String) s.getAttribute(SESSION_ROLE);
        return "SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role);
    }

    private ResponseEntity<?> unauth() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Please login first."));
    }

    public record SellerUpdateRequest(String name, String contact, String location, String image, String email) {}
    public record PasswordUpdateRequest(String oldPassword, String newPassword) {}
}
