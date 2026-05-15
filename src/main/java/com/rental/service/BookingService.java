package com.rental.service;

import com.rental.model.Booking;
import com.rental.repository.BookingRepository;
import com.rental.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Component 05 — Purchase Management Service (Member 5)
 *
 * Owns buyer-side booking logic:
 *  - createBooking   (CREATE — buyer submits offer)
 *  - updateOffer     (UPDATE — buyer edits pending offer)
 *  - acceptCounterOffer (UPDATE — buyer accepts seller's counter)
 *  - cancelBooking   (DELETE — buyer cancels pending request)
 *
 * OOP — Encapsulation: booking state (offerAmount, status, isPaid) only changes
 *        through these service methods. Controller never mutates state directly.
 *        Polymorphism: Booking behaves differently at each status stage.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;

    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
    }

    // CREATE — Buyer submits a purchase offer on a vehicle
    public Booking createBooking(Long customerId, String customerRole,
                                  Long vehicleId, double offerAmount, String offerMessage) {
        var vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        if (!"APPROVED".equalsIgnoreCase(vehicle.getStatus()))
            throw new IllegalArgumentException("This vehicle is not available for purchase.");

        boolean isSeller = "SELLER".equalsIgnoreCase(customerRole) || "DEALER".equalsIgnoreCase(customerRole);
        if (isSeller && customerId.equals(vehicle.getSellerId()))
            throw new IllegalArgumentException("You cannot buy your own vehicle.");

        Booking b = new Booking();
        b.setVehicleId(vehicleId);
        b.setRenterId(vehicle.getSellerId());
        b.setCustomerId(customerId);
        b.setOfferAmount(offerAmount > 0 ? offerAmount : vehicle.getPrice());
        b.setOfferMessage(offerMessage == null ? "" : offerMessage.trim());
        b.setTotalAmount(vehicle.getPrice());
        b.setStatus("PENDING");
        b.setPaid(false);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    // Overload for backward compatibility
    public Booking createBooking(Long customerId, Long vehicleId,
                                  double offerAmount, String offerMessage) {
        return createBooking(customerId, "BUYER", vehicleId, offerAmount, offerMessage);
    }

    // READ — Get all bookings for a buyer
    public List<Booking> getBookingsByCustomer(Long customerId) {
        return bookingRepository.findAll().stream()
                .filter(b -> customerId.equals(b.getCustomerId())).toList();
    }

    public Optional<Booking> findById(Long id) { return bookingRepository.findById(id); }

    // UPDATE — Buyer edits offer while PENDING
    public Booking updateOffer(Long customerId, Long bookingId,
                                double offerAmount, String offerMessage) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.getCustomerId().equals(customerId))
            throw new IllegalArgumentException("Not your booking.");
        if (!"PENDING".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Can only update PENDING requests.");
        if (offerAmount > 0) b.setOfferAmount(offerAmount);
        if (offerMessage != null) b.setOfferMessage(offerMessage.trim());
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.update(b);
    }

    // UPDATE — Buyer accepts a seller's counter-offer: COUNTERED → CONFIRMED
    public Booking acceptCounterOffer(Long customerId, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.getCustomerId().equals(customerId))
            throw new IllegalArgumentException("Not your booking.");
        if (!"COUNTERED".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("No counter-offer to accept on this booking.");
        // Accept the counter — use the seller's counter price
        b.setOfferAmount(b.getCounterAmount());
        b.setStatus("CONFIRMED");
        b.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.update(b);
    }

    // DELETE — Buyer cancels a pending request
    public void cancelBooking(Long customerId, Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.getCustomerId().equals(customerId))
            throw new IllegalArgumentException("Not your booking.");
        if (!"PENDING".equalsIgnoreCase(b.getStatus()) && !"COUNTERED".equalsIgnoreCase(b.getStatus()))
            throw new IllegalArgumentException("Can only cancel PENDING or COUNTERED requests.");
        bookingRepository.deleteById(bookingId);
    }

    // Used by admin
    public void deleteBookingByAdmin(Long bookingId) {
        if (!bookingRepository.deleteById(bookingId))
            throw new IllegalArgumentException("Booking not found.");
    }

    // Used by SellingService — kept accessible
    public List<Booking> getAllBookings() { return bookingRepository.findAll(); }
}
