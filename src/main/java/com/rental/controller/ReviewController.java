package com.rental.controller;

import com.rental.model.Review;
import com.rental.service.ReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Component 06: Review & Feedback Management
// Buyers can review sellers. Sellers can review buyers.
// Users can only edit/delete their OWN reviews.
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ROLE     = "AUTH_ROLE";

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // CREATE - Any logged-in user submits a review
    // targetType = "vehicle" | "seller" | "buyer"
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReviewRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        String role = (String) session.getAttribute(SESSION_ROLE);
        try {
            String targetType = req.targetType() == null ? "vehicle" : req.targetType().toLowerCase();
            // Sellers can review buyers (targetType="buyer"), buyers review vehicles/sellers
            String reviewerRole = (String) session.getAttribute(SESSION_ROLE);
            if (reviewerRole == null) reviewerRole = "BUYER";
            Review r = reviewService.createReview(userId, reviewerRole.toUpperCase(), req.targetId(), targetType,
                    req.rating(), req.comment());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Review submitted.", "review", reviewView(r)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - All reviews for a vehicle (public)
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<?> forVehicle(@PathVariable Long vehicleId) {
        var reviews = reviewService.getReviewsForTarget(vehicleId, "vehicle")
                .stream().map(this::reviewView).toList();
        double avg = reviewService.getAverageRating(vehicleId, "vehicle");
        return ResponseEntity.ok(Map.of("reviews", reviews,
                "averageRating", Math.round(avg * 10.0) / 10.0, "count", reviews.size()));
    }

    // READ - All reviews for a seller (public)
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> forSeller(@PathVariable Long sellerId) {
        var reviews = reviewService.getReviewsForTarget(sellerId, "seller")
                .stream().map(this::reviewView).toList();
        double avg = reviewService.getAverageRating(sellerId, "seller");
        return ResponseEntity.ok(Map.of("reviews", reviews,
                "averageRating", Math.round(avg * 10.0) / 10.0, "count", reviews.size()));
    }

    // READ - All reviews written ABOUT a buyer (public — others can see)
    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<?> forBuyer(@PathVariable Long buyerId) {
        var reviews = reviewService.getReviewsForTarget(buyerId, "buyer")
                .stream().map(this::reviewView).toList();
        double avg = reviewService.getAverageRating(buyerId, "buyer");
        return ResponseEntity.ok(Map.of("reviews", reviews,
                "averageRating", Math.round(avg * 10.0) / 10.0, "count", reviews.size()));
    }

    // READ - Reviews written BY the logged-in user (role-aware to prevent ID collision)
    @GetMapping("/mine/customer")
    public ResponseEntity<?> myReviews(HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        String role = (String) session.getAttribute(SESSION_ROLE);
        if (role == null) role = "BUYER";
        // Use role-aware query: prevents seller ID=1 and buyer ID=1 colliding
        return ResponseEntity.ok(Map.of("reviews",
                reviewService.getReviewsByReviewerAndRole(userId, role.toUpperCase())
                        .stream().map(this::reviewView).toList()));
    }

    // READ - Single review by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        Review r = reviewService.findById(id).orElse(null);
        if (r == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Review not found."));
        return ResponseEntity.ok(Map.of("review", reviewView(r)));
    }

    // UPDATE - Owner edits their own review ONLY
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody ReviewUpdateRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Review r = reviewService.updateReview(id, userId, req.rating(), req.comment());
            return ResponseEntity.ok(Map.of("message", "Review updated.", "review", reviewView(r)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Owner deletes their own review ONLY
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            reviewService.deleteReview(id, userId);
            return ResponseEntity.ok(Map.of("message", "Review deleted."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Admin removes any review
    @DeleteMapping("/admin/{reviewId}")
    public ResponseEntity<?> adminDelete(@PathVariable Long reviewId, HttpSession session) {
        String role = (String) session.getAttribute(SESSION_ROLE);
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPERADMIN".equalsIgnoreCase(role))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin only."));
        try {
            reviewService.deleteReviewByAdmin(reviewId);
            return ResponseEntity.ok(Map.of("message", "Review removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> reviewView(Review r) {
        return Map.ofEntries(
                Map.entry("id", r.getId()),
                Map.entry("reviewerId", r.getReviewerId()),
                Map.entry("reviewerRole", r.getReviewerRole() == null ? "BUYER" : r.getReviewerRole()),
                Map.entry("targetId", r.getTargetId()),
                Map.entry("targetType", r.getTargetType() == null ? "" : r.getTargetType()),
                Map.entry("comment", r.getContent() == null ? "" : r.getContent()),
                Map.entry("rating", r.getRating()),
                Map.entry("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()),
                Map.entry("updatedAt", r.getUpdatedAt() == null ? "" : r.getUpdatedAt().toString()));
    }

    private Long uid(HttpSession s) {
        Object r = s.getAttribute(SESSION_USER_ID);
        if (r instanceof Long l) return l;
        if (r instanceof Integer i) return i.longValue();
        return null;
    }

    private ResponseEntity<?> unauth() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Please login first."));
    }

    public record ReviewRequest(Long targetId, String targetType, int rating, String comment) {}
    public record ReviewUpdateRequest(int rating, String comment) {}
}
