package com.rental.repository;

import com.rental.model.Review;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 06: Review & Feedback Management - Repository
@Repository
public class ReviewRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public ReviewRepository(
            @Value("${app.data.reviews-file:src/main/resources/data/reviews.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Review> findAll() {
        List<Review> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) list.add(parseLine(line));
        }
        return list;
    }

    public synchronized Optional<Review> findById(Long id) {
        return findAll().stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public synchronized List<Review> findByTarget(Long targetId, String targetType) {
        return findAll().stream()
                .filter(r -> Objects.equals(r.getTargetId(), targetId)
                        && Objects.equals(r.getTargetType(), targetType))
                .toList();
    }

    public synchronized List<Review> findByReviewerId(Long reviewerId) {
        return findAll().stream().filter(r -> reviewerId.equals(r.getReviewerId())).toList();
    }

    // Find reviews written by a specific user with a specific role (prevents ID namespace collision)
    public synchronized List<Review> findByReviewerIdAndRole(Long reviewerId, String role) {
        return findAll().stream()
                .filter(r -> reviewerId.equals(r.getReviewerId()) &&
                        role.equalsIgnoreCase(r.getReviewerRole()))
                .toList();
    }

    public synchronized Review save(Review review) {
        List<Review> all = findAll();
        if (review.getId() == null) {
            review.setId(nextId(all));
            all.add(review);
        } else {
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId().equals(review.getId())) {
                    all.set(i, review); found = true; break;
                }
            }
            if (!found) all.add(review);
        }
        writeAll(all);
        return review;
    }

    public synchronized boolean deleteById(Long id) {
        List<Review> all = findAll();
        boolean removed = all.removeIf(r -> r.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<Review> list) {
        return list.stream().map(Review::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Review> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // id|reviewerId|reviewerRole|targetId|targetType|content|rating|createdAt|updatedAt
    private String toLine(Review r) {
        return r.getId() + DELIM + r.getReviewerId() + DELIM
                + (r.getReviewerRole() == null ? "BUYER" : r.getReviewerRole()) + DELIM
                + r.getTargetId() + DELIM
                + esc(r.getTargetType()) + DELIM + esc(r.getContent()) + DELIM + r.getRating() + DELIM
                + (r.getCreatedAt() == null ? "" : FMT.format(r.getCreatedAt())) + DELIM
                + (r.getUpdatedAt() == null ? "" : FMT.format(r.getUpdatedAt()));
    }

    private Review parseLine(String line) {
        String[] p = line.split("\\|", -1);
        Review r = new Review();
        if (p.length >= 9) {
            // New format: id|reviewerId|reviewerRole|targetId|targetType|content|rating|createdAt|updatedAt
            r.setId(Long.parseLong(p[0].trim()));
            r.setReviewerId(Long.parseLong(p[1].trim()));
            r.setReviewerRole(p[2].trim());
            r.setTargetId(Long.parseLong(p[3].trim()));
            r.setTargetType(unesc(p[4]));
            r.setContent(unesc(p[5]));
            r.setRating(Integer.parseInt(p[6].trim()));
            try { r.setCreatedAt(LocalDateTime.parse(p[7], FMT)); } catch (Exception ignored) {}
            try { r.setUpdatedAt(LocalDateTime.parse(p[8], FMT)); } catch (Exception ignored) {}
        } else if (p.length >= 8) {
            // Old format: id|reviewerId|targetId|targetType|content|rating|createdAt|updatedAt
            r.setId(Long.parseLong(p[0].trim()));
            r.setReviewerId(Long.parseLong(p[1].trim()));
            r.setReviewerRole("BUYER"); // default for old records
            r.setTargetId(Long.parseLong(p[2].trim()));
            r.setTargetType(unesc(p[3]));
            r.setContent(unesc(p[4]));
            r.setRating(Integer.parseInt(p[5].trim()));
            try { r.setCreatedAt(LocalDateTime.parse(p[6], FMT)); } catch (Exception ignored) {}
            try { r.setUpdatedAt(LocalDateTime.parse(p[7], FMT)); } catch (Exception ignored) {}
        }
        return r;
    }

    private LocalDateTime parsedt(String v) {
        return (v == null || v.isBlank()) ? null : LocalDateTime.parse(v, FMT);
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private String unesc(String v) {
        if (v == null || v.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (char c : v.toCharArray()) {
            if (esc) { sb.append(c == 'n' ? '\n' : c); esc = false; }
            else if (c == '\\') esc = true;
            else sb.append(c);
        }
        return sb.toString();
    }
}
