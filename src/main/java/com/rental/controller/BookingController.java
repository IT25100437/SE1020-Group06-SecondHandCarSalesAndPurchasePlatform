package com.rental.controller;

import com.rental.model.Booking;
import com.rental.model.Payment;
import com.rental.service.BookingService;
import com.rental.service.PaymentService;
import com.rental.service.VehicleService;
import com.rental.service.SellerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Component 05 — Purchase Management Controller (Member 5)
 *
 * Owns the complete buyer-side purchase and payment lifecycle:
 *   CREATE → Submit a new purchase offer on a vehicle
 *   CREATE → Add a payment card
 *   CREATE → Pay for a confirmed booking
 *   READ   → View own purchase requests and statuses
 *   READ   → View saved payment cards
 *   READ   → View payment transaction history
 *   UPDATE → Edit offer amount/message while PENDING
 *   UPDATE → Accept a seller's counter-offer
 *   UPDATE → Update saved card details
 *   DELETE → Cancel a pending purchase request
 *   DELETE → Remove a saved payment card
 *
 * OOP: Encapsulation (cardNumber masked, CVV stored as ***),
 *      Abstraction (PaymentService.maskCard hides masking logic).
 */
@RestController
@RequestMapping("/api")
public class BookingController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ROLE    = "AUTH_ROLE";

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final VehicleService vehicleService;
    private final SellerService sellerService;

    public BookingController(BookingService bookingService,
                              PaymentService paymentService,
                              VehicleService vehicleService,
                              SellerService sellerService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.vehicleService = vehicleService;
        this.sellerService  = sellerService;
    }

    // ─── CREATE ────────────────────────────────────────────────────────

    /** CREATE — Buyer submits a purchase offer on a vehicle */
    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            String role = (String) session.getAttribute(SESSION_ROLE);
            Booking b = bookingService.createBooking(userId, role, req.vehicleId(),
                    req.offerAmount(), req.offerMessage());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Purchase request submitted.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** CREATE — Buyer adds a payment card */
    @PostMapping("/payments/cards")
    public ResponseEntity<?> addCard(@RequestBody CardRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Payment card = paymentService.addCard(userId, req.cardHolderName(), req.cardNumber(),
                    req.expiryMonth(), req.expiryYear(), req.cvv());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Card added.", "card", paymentView(card)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** CREATE — Buyer pays for a confirmed booking using a saved card */
    @PostMapping("/payments/bookings/{bookingId}/pay")
    public ResponseEntity<?> pay(@PathVariable Long bookingId, @RequestBody PayRequest req,
                                  HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Payment p = paymentService.payForBooking(userId, bookingId, req.cardId());
            return ResponseEntity.ok(Map.of("message", "Payment successful.", "payment", paymentView(p)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── READ ──────────────────────────────────────────────────────────

    /** READ — Buyer views all their own purchase requests */
    @GetMapping("/bookings/mine/customer")
    public ResponseEntity<?> myBookings(HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        return ResponseEntity.ok(Map.of("bookings",
                bookingService.getBookingsByCustomer(userId)
                        .stream().map(this::bookingView).toList()));
    }

    /** READ — Buyer views their saved payment cards */
    @GetMapping("/payments/cards")
    public ResponseEntity<?> getCards(HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        return ResponseEntity.ok(Map.of("cards",
                paymentService.getCards(userId).stream().map(this::paymentView).toList()));
    }

    /** READ — Buyer views their payment transaction history */
    @GetMapping("/payments/transactions")
    public ResponseEntity<?> getPaymentTransactions(HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        return ResponseEntity.ok(Map.of("transactions",
                paymentService.getTransactions(userId).stream().map(this::paymentView).toList()));
    }

    // ─── UPDATE ────────────────────────────────────────────────────────

    /** UPDATE — Buyer edits their offer amount/message (only while PENDING) */
    @PutMapping("/bookings/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable Long id,
                                           @RequestBody OfferUpdateRequest req, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Booking b = bookingService.updateOffer(userId, id, req.offerAmount(), req.offerMessage());
            return ResponseEntity.ok(Map.of("message", "Offer updated.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** UPDATE — Buyer accepts a seller's counter-offer (status COUNTERED → CONFIRMED) */
    @PostMapping("/bookings/{id}/accept-counter")
    public ResponseEntity<?> acceptCounter(@PathVariable Long id, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Booking b = bookingService.acceptCounterOffer(userId, id);
            return ResponseEntity.ok(Map.of("message", "Counter-offer accepted.", "booking", bookingView(b)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** UPDATE — Buyer updates saved card details */
    @PutMapping("/payments/cards/{cardId}")
    public ResponseEntity<?> updateCard(@PathVariable Long cardId, @RequestBody CardRequest req,
                                         HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            Payment card = paymentService.updateCard(userId, cardId, req.cardHolderName(),
                    req.cardNumber(), req.expiryMonth(), req.expiryYear(), req.cvv());
            return ResponseEntity.ok(Map.of("message", "Card updated.", "card", paymentView(card)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── DELETE ────────────────────────────────────────────────────────

    /** DELETE — Buyer cancels a pending purchase request */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            bookingService.cancelBooking(userId, id);
            return ResponseEntity.ok(Map.of("message", "Purchase request cancelled."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** DELETE — Buyer removes a saved payment card */
    @DeleteMapping("/payments/cards/{cardId}")
    public ResponseEntity<?> deleteCard(@PathVariable Long cardId, HttpSession session) {
        Long userId = uid(session);
        if (userId == null) return unauth();
        try {
            paymentService.deleteCard(userId, cardId);
            return ResponseEntity.ok(Map.of("message", "Card removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> bookingView(Booking b) {
        String vehicleTitle = "Vehicle #" + b.getVehicleId();
        String vehicleImage = "";
        String sellerName   = "Seller #"  + b.getRenterId();
        try {
            var v = vehicleService.findById(b.getVehicleId()).orElse(null);
            if (v != null) {
                vehicleTitle = v.getTitle();
                vehicleImage = (v.getImages() == null || v.getImages().isEmpty()) ? "" : v.getImages().get(0);
            }
        } catch (Exception ignored) {}
        try {
            var s = sellerService.findById(b.getRenterId()).orElse(null);
            if (s != null) sellerName = s.getName();
        } catch (Exception ignored) {}
        return Map.ofEntries(
                Map.entry("id",           b.getId()),
                Map.entry("vehicleId",    b.getVehicleId()),
                Map.entry("vehicleTitle", vehicleTitle),
                Map.entry("vehicleImage", vehicleImage),
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

    private Map<String, Object> paymentView(Payment p) {
        return Map.ofEntries(
                Map.entry("id",             p.getId()),
                Map.entry("customerId",     p.getCustomerId()),
                Map.entry("bookingId",      p.getBookingId()),
                Map.entry("type",           p.getType() == null ? "" : p.getType()),
                Map.entry("cardHolderName", p.getCardHolderName() == null ? "" : p.getCardHolderName()),
                Map.entry("cardNumber",     p.getCardNumber() == null ? "" : p.getCardNumber()),
                Map.entry("amount",         p.getAmount()),
                Map.entry("status",         p.getStatus() == null ? "" : p.getStatus()),
                Map.entry("createdAt",      p.getCreatedAt() == null ? "" : p.getCreatedAt().toString()));
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

    public record BookingRequest(Long vehicleId, double offerAmount, String offerMessage) {}
    public record OfferUpdateRequest(double offerAmount, String offerMessage) {}
    public record PayRequest(Long cardId) {}
    public record CardRequest(String cardHolderName, String cardNumber,
                               String expiryMonth, String expiryYear, String cvv) {}
}
