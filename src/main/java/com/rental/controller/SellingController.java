package com.rental.controller;

import com.rental.model.Booking;
import com.rental.model.Transaction;
import com.rental.service.SellingService;
import com.rental.service.TransactionService;
import com.rental.service.VehicleService;
import com.rental.service.SellerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Component 03 — Selling Management Controller (Member 3)
 *
 * Owns the complete seller-side transaction lifecycle:
 *   CREATE  → Record a completed sale (Transaction auto-created via markAsSold)
 *   CREATE  → Seller posts a counter-offer back to the buyer
 *   READ    → View all incoming purchase requests on seller's vehicles
 *   READ    → View completed sales / transaction history
 *   READ    → Sales summary report (total revenue, vehicles sold, avg price)
 *   UPDATE  → Confirm a buyer's request  (PENDING → CONFIRMED)
 *   UPDATE  → Reject a buyer's request   (PENDING → REJECTED)
 *   UPDATE  → Mark as SOLD               (CONFIRMED → SOLD, cascades 3 changes)
 *   UPDATE  → Update transaction status  (flag as CANCELLED etc.)
 *   DELETE  → Remove invalid/cancelled transaction record
 *
 * OOP: Encapsulation (Transaction fields private), Abstraction (markAsSold triggers
 * 3 cascaded changes), Polymorphism (IndividualSeller vs DealerSeller flows differ).
 */
@RestController
@RequestMapping("/api/selling")
public class SellingController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ROLE    = "AUTH_ROLE";

    private final SellingService sellingService;
    private final TransactionService transactionService;
    private final VehicleService vehicleService;
    private final SellerService sellerService;

    public SellingController(SellingService sellingService,
                              TransactionService transactionService,
                              VehicleService vehicleService,
                              SellerService sellerService) {
        this.sellingService   = sellingService;
        this.transactionService = transactionService;
        this.vehicleService   = vehicleService;
        this.sellerService    = sellerService;
    }

    // ─── READ ──────────────────────────────────────────────────────────

    /** READ — Seller views all incoming purchase requests on their vehicles */
    @GetMapping("/requests")
    public ResponseEntity<?> getRequests(HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        return ResponseEntity.ok(Map.of("bookings",
                sellingService.getRequestsForSeller(sellerId)
                        .stream().map(b -> bookingView(b)).toList()));
    }

    /** READ — Seller views completed sales / transaction history */
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        return ResponseEntity.ok(Map.of("transactions",
                transactionService.findBySeller(sellerId)));
    }

    /** READ — Sales summary report: total revenue, vehicles sold, average price */
    @GetMapping("/summary")
    public ResponseEntity<?> getSalesSummary(HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        List<Transaction> txList = transactionService.findBySeller(sellerId);
        long totalSold    = txList.size();
        double totalRev   = txList.stream().mapToDouble(Transaction::getAmount).sum();
        double avgPrice   = totalSold > 0 ? totalRev / totalSold : 0;
        long pendingCount = sellingService.getRequestsForSeller(sellerId).stream()
                .filter(b -> "PENDING".equalsIgnoreCase(b.getStatus())).count();
        return ResponseEntity.ok(Map.of(
                "totalSold",    totalSold,
                "totalRevenue", totalRev,
                "averagePrice", Math.round(avgPrice * 100.0) / 100.0,
                "pendingRequests", pendingCount
        ));
    }

    // ─── CREATE ────────────────────────────────────────────────────────

    /**
     * CREATE (Manual) — Seller manually records a completed sale transaction.
     * For cash deals or offline sales not processed through the platform.
     * A new Transaction record is written directly to transactions.txt.
     */
    @PostMapping("/transactions/manual")
    public ResponseEntity<?> createManualTransaction(@RequestBody ManualSaleRequest req,
                                                      HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            Transaction t = sellingService.createManualTransaction(
                    sellerId, req.vehicleId(), req.amount(),
                    req.buyerId(), req.paymentMethod(), req.notes());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Transaction recorded successfully.", "transaction", t));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * CREATE — Seller posts a counter-offer (different price) back to the buyer.
     * This is the seller's own Create operation — generates a new counter-offer record.
     */
    @PostMapping("/requests/{bookingId}/counter")
    public ResponseEntity<?> counterOffer(@PathVariable Long bookingId,
                                           @RequestBody CounterOfferRequest req,
                                           HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            Booking b = sellingService.postCounterOffer(sellerId, bookingId,
                    req.counterAmount(), req.sellerNote());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Counter-offer sent to buyer.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── UPDATE ────────────────────────────────────────────────────────

    /** UPDATE — Seller confirms a buyer's request: PENDING → CONFIRMED */
    @PostMapping("/requests/{bookingId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long bookingId, HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            Booking b = sellingService.confirmRequest(sellerId, bookingId);
            return ResponseEntity.ok(Map.of("message", "Request confirmed.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** UPDATE — Seller rejects a buyer's request: PENDING → REJECTED */
    @PostMapping("/requests/{bookingId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long bookingId, HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            Booking b = sellingService.rejectRequest(sellerId, bookingId);
            return ResponseEntity.ok(Map.of("message", "Request rejected.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * UPDATE — Seller marks confirmed booking as SOLD.
     * Abstraction: one call cascades 3 changes:
     *   1. Booking status → SOLD
     *   2. Transaction record CREATED (stored in transactions.txt)
     *   3. Vehicle status → SOLD (removed from public listings)
     */
    @PostMapping("/requests/{bookingId}/sold")
    public ResponseEntity<?> markSold(@PathVariable Long bookingId, HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            Booking b = sellingService.completeSale(sellerId, bookingId);
            return ResponseEntity.ok(Map.of(
                    "message", "Sale completed. Transaction recorded. Vehicle marked as SOLD.",
                    "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** UPDATE — Update transaction status (COMPLETED / CANCELLED) */
    @PutMapping("/transactions/{txId}/status")
    public ResponseEntity<?> updateTxStatus(@PathVariable Long txId,
                                             @RequestBody StatusRequest req,
                                             HttpSession session) {
        if (uid(session) == null || !isSeller(session)) return unauth();
        try {
            Transaction t = transactionService.updateStatus(txId, req.status());
            return ResponseEntity.ok(Map.of("message", "Transaction status updated.", "transaction", t));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── DELETE ────────────────────────────────────────────────────────

    /** DELETE — Seller dismisses/removes a booking request from their view */
    @DeleteMapping("/requests/{bookingId}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long bookingId, HttpSession session) {
        Long sellerId = uid(session);
        if (sellerId == null || !isSeller(session)) return unauth();
        try {
            // Verify booking belongs to this seller before deleting
            sellingService.deleteRequest(sellerId, bookingId);
            return ResponseEntity.ok(Map.of("message", "Request removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** DELETE — Seller removes an invalid or cancelled transaction record */
    @DeleteMapping("/transactions/{txId}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long txId, HttpSession session) {
        if (uid(session) == null || !isSeller(session)) return unauth();
        try {
            transactionService.delete(txId);
            return ResponseEntity.ok(Map.of("message", "Transaction removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> bookingView(Booking b) {
        String vehicleTitle = "Vehicle #" + b.getVehicleId();
        String sellerName   = "Seller #"  + b.getRenterId();
        try {
            var v = vehicleService.findById(b.getVehicleId()).orElse(null);
            if (v != null) vehicleTitle = v.getTitle();
        } catch (Exception ignored) {}
        try {
            var s = sellerService.findById(b.getRenterId()).orElse(null);
            if (s != null) sellerName = s.getName();
        } catch (Exception ignored) {}
        return Map.ofEntries(
                Map.entry("id",           b.getId()),
                Map.entry("vehicleId",    b.getVehicleId()),
                Map.entry("vehicleTitle", vehicleTitle),
                Map.entry("sellerName",   sellerName),
                Map.entry("customerId",   b.getCustomerId()),
                Map.entry("sellerId",     b.getRenterId()),
                Map.entry("offerAmount",  b.getOfferAmount()),
                Map.entry("counterAmount",b.getCounterAmount()),
                Map.entry("sellerNote",   b.getSellerNote() == null ? "" : b.getSellerNote()),
                Map.entry("offerMessage", b.getOfferMessage() == null ? "" : b.getOfferMessage()),
                Map.entry("totalAmount",  b.getTotalAmount()),
                Map.entry("status",       b.getStatus() == null ? "" : b.getStatus()),
                Map.entry("paid",         b.isPaid()),
                Map.entry("createdAt",    b.getCreatedAt() == null ? "" : b.getCreatedAt().toString()));
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
                .body(Map.of("message", "Seller login required."));
    }

    public record CounterOfferRequest(double counterAmount, String sellerNote) {}
    public record ManualSaleRequest(Long vehicleId, double amount, Long buyerId, String paymentMethod, String notes) {}
    public record StatusRequest(String status) {}
}
