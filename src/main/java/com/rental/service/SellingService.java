package com.rental.service;

import com.rental.model.Booking;
import com.rental.model.Transaction;
import com.rental.repository.BookingRepository;
import com.rental.repository.TransactionRepository;
import com.rental.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Component 03 — Selling Management Service (Member 3)
 *
 * Owns the seller-side business logic:
 *  - Counter-offer creation (CREATE)
 *  - Viewing incoming requests (READ)
 *  - Confirm / Reject request state transitions (UPDATE)
 *  - completeSale() — the key abstraction that cascades 3 changes at once (UPDATE + CREATE)
 *
 * OOP — Abstraction: completeSale() hides the complexity of updating booking,
 *        creating a transaction record, and marking the vehicle SOLD in one call.
 *        Encapsulation: all state transition rules enforced here, not in the controller.
 */
@Service
public class SellingService {

    private final BookingRepository bookingRepository;
    private final TransactionRepository transactionRepository;
    private final VehicleRepository vehicleRepository;

    public SellingService(BookingRepository bookingRepository,
                           TransactionRepository transactionRepository,
                           VehicleRepository vehicleRepository) {
        this.bookingRepository    = bookingRepository;
        this.transactionRepository = transactionRepository;
        this.vehicleRepository    = vehicleRepository;
    }

    // READ — Get all purchase requests for a specific seller
    public List<Booking> getRequestsForSeller(Long sellerId) {
        return bookingRepository.findAll().stream()
                .filter(b -> sellerId.equals(b.getRenterId()))
                .toList();
    }

    /**
     * CREATE (Manual) — Seller records a sale transaction manually.
     * Used for cash deals, in-person agreements, or offline sales
     * where no booking was created through the platform.
     *
     * This is a direct CREATE operation: a new Transaction object is
     * instantiated and written to transactions.txt immediately.
     */
    public Transaction createManualTransaction(Long sellerId, Long vehicleId,
                                                double amount, Long buyerId,
                                                String paymentMethod, String notes) {
        if (amount <= 0)
            throw new IllegalArgumentException("Sale amount must be greater than 0.");
        if (vehicleId == null)
            throw new IllegalArgumentException("Vehicle ID is required.");

        // Verify vehicle belongs to this seller
        vehicleRepository.findById(vehicleId).ifPresent(v -> {
            if (!v.getSellerId().equals(sellerId))
                throw new IllegalArgumentException("This vehicle does not belong to you.");
        });

        Transaction t = new Transaction();
        t.setVehicleId(vehicleId);
        t.setSellerId(sellerId);
        t.setBuyerId(buyerId != null && buyerId > 0 ? buyerId : 0L);
        t.setAmount(amount);
        t.setStatus("COMPLETED");
        t.setPaymentMethod(paymentMethod == null ? "cash" : paymentMethod);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(t);

        // Also mark the vehicle as SOLD if it isn't already
        vehicleRepository.findById(vehicleId).ifPresent(v -> {
            if (!"SOLD".equalsIgnoreCase(v.getStatus())) {
                v.setStatus("SOLD");
                v.setUpdatedAt(LocalDateTime.now());
                vehicleRepository.update(v);
            }
        });

        return saved;
    }

    /**
     * CREATE — Seller posts a counter-offer with a revised price and a note.
     * Status changes to COUNTERED so buyer knows a counter has been sent.
     * This is Member 3's unique Create operation.
     */
    public Booking postCounterOffer(Long sellerId, Long bookingId,
                                     double counterAmount, String sellerNote) {
        Booking b = getSellerBooking(sellerId, bookingId);
        if (!"PENDING".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Can only counter a PENDING request.");
        if (counterAmount <= 0)
            throw new IllegalArgumentException("Counter-offer amount must be greater than 0.");
        b.setCounterAmount(counterAmount);
        b.setSellerNote(sellerNote == null ? "" : sellerNote.trim());
        b.setStatus("COUNTERED");
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.update(b);
    }

    // UPDATE — Seller confirms a request: PENDING/COUNTERED → CONFIRMED
    public Booking confirmRequest(Long sellerId, Long bookingId) {
        Booking b = getSellerBooking(sellerId, bookingId);
        if (!"PENDING".equalsIgnoreCase(b.getStatus()) && !"COUNTERED".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Only PENDING or COUNTERED requests can be confirmed.");
        b.setStatus("CONFIRMED");
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.update(b);
    }

    // UPDATE — Seller rejects a request: PENDING → REJECTED
    public Booking rejectRequest(Long sellerId, Long bookingId) {
        Booking b = getSellerBooking(sellerId, bookingId);
        if (!"PENDING".equalsIgnoreCase(b.getStatus()) && !"COUNTERED".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Only PENDING or COUNTERED requests can be rejected.");
        b.setStatus("REJECTED");
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.update(b);
    }

    /**
     * UPDATE + CREATE (cascade) — completeSale() is the core of Selling Management.
     *
     * Abstraction: one method call triggers THREE changes:
     *   1. Booking status → SOLD
     *   2. NEW Transaction record written to transactions.txt (the CREATE)
     *   3. Vehicle status → SOLD (removed from public browse listings)
     *
     * Precondition: booking must be CONFIRMED and buyer must have paid.
     */
    public Booking completeSale(Long sellerId, Long bookingId) {
        Booking b = getSellerBooking(sellerId, bookingId);
        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Booking must be CONFIRMED before marking as sold.");
        if (!b.isPaid())
            throw new IllegalArgumentException("Cannot complete sale — buyer has not paid yet.");

        // Step 1: Mark booking SOLD
        b.setStatus("SOLD");
        b.setUpdatedAt(LocalDateTime.now());
        bookingRepository.update(b);

        // Step 2: CREATE — write a new Transaction record
        Transaction t = new Transaction();
        t.setVehicleId(b.getVehicleId());
        t.setSellerId(sellerId);
        t.setBuyerId(b.getCustomerId());
        t.setAmount(b.getOfferAmount());
        t.setStatus("COMPLETED");
        t.setPaymentMethod("card");
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(t);

        // Step 3: Mark vehicle SOLD — removes from public listings
        vehicleRepository.findById(b.getVehicleId()).ifPresent(v -> {
            v.setStatus("SOLD");
            v.setUpdatedAt(LocalDateTime.now());
            vehicleRepository.update(v);
        });

        return b;
    }

    // DELETE — Seller removes a booking request from their list
    public void deleteRequest(Long sellerId, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.getRenterId().equals(sellerId))
            throw new IllegalArgumentException("This request does not belong to you.");
        bookingRepository.deleteById(bookingId);
    }

    // Private helper — verifies the booking belongs to this seller
    private Booking getSellerBooking(Long sellerId, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.getRenterId().equals(sellerId))
            throw new IllegalArgumentException("This booking does not belong to you.");
        return b;
    }
}
