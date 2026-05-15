package com.rental.service;

import com.rental.model.Review;
import com.rental.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    public ReviewService(ReviewRepository reviewRepository) { this.reviewRepository = reviewRepository; }

    // CREATE - Any user submits a review (vehicle, seller, or buyer)
    public Review createReview(Long reviewerId, String reviewerRole, Long targetId, String targetType, int rating, String content) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Review content is required.");
        if (targetId == null) throw new IllegalArgumentException("Target ID is required.");

        String normType = (targetType == null || targetType.isBlank()) ? "vehicle" : targetType.trim().toLowerCase();
        // Allow vehicle, seller, AND buyer reviews
        if (!normType.equals("vehicle") && !normType.equals("seller") && !normType.equals("buyer"))
            throw new IllegalArgumentException("Target type must be 'vehicle', 'seller', or 'buyer'.");

        Review review = new Review();
        review.setReviewerId(reviewerId);
        review.setReviewerRole(reviewerRole == null ? "BUYER" : reviewerRole.toUpperCase());
        review.setTargetId(targetId);
        review.setTargetType(normType);
        review.setRating(rating);
        review.setContent(content.trim());
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public List<Review> getReviewsForTarget(Long targetId, String targetType) {
        return reviewRepository.findByTarget(targetId, targetType);
    }

    public double getAverageRating(Long targetId, String targetType) {
        List<Review> reviews = reviewRepository.findByTarget(targetId, targetType);
        if (reviews.isEmpty()) return 0.0;
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
    }

    public List<Review> getReviewsByReviewer(Long reviewerId) {
        return reviewRepository.findByReviewerId(reviewerId);
    }

    // Role-aware: only returns reviews written by this user IN this role
    // Prevents ID namespace collision between buyers and sellers
    public List<Review> getReviewsByReviewerAndRole(Long reviewerId, String role) {
        return reviewRepository.findByReviewerIdAndRole(reviewerId, role);
    }

    public List<Review> getAllReviews() { return reviewRepository.findAll(); }

    public Optional<Review> findById(Long id) { return reviewRepository.findById(id); }

    // UPDATE - Owner edits own review only
    public Review updateReview(Long reviewId, Long reviewerId, int rating, String content) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (!review.getReviewerId().equals(reviewerId))
            throw new IllegalArgumentException("You can only edit your own reviews.");
        if (rating >= 1 && rating <= 5) review.setRating(rating);
        if (content != null && !content.isBlank()) review.setContent(content.trim());
        review.setUpdatedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    // DELETE - Owner deletes own review
    public void deleteReview(Long reviewId, Long reviewerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (!review.getReviewerId().equals(reviewerId))
            throw new IllegalArgumentException("You can only delete your own reviews.");
        reviewRepository.deleteById(reviewId);
    }

    // DELETE - Admin removes any review
    public void deleteReviewByAdmin(Long reviewId) {
        reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        reviewRepository.deleteById(reviewId);
    }
}
