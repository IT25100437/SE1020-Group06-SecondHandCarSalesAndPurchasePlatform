package com.rental.service;

import com.rental.model.Booking;
import com.rental.model.Payment;
import com.rental.repository.BookingRepository;
import com.rental.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Component 04: Purchase Management - Payment Service
// Encapsulation: card number and CVV are masked before storage
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    // CREATE - Buyer adds a payment card
    public Payment addCard(Long userId, String cardHolderName, String cardNumber,
                           String expiryMonth, String expiryYear, String cvv) {
        validateCard(cardNumber, expiryMonth, expiryYear, cvv);
        Payment card = new Payment();
        card.setCustomerId(userId);
        card.setBookingId(0L);
        card.setType("CARD");
        card.setCardHolderName(cardHolderName == null ? "" : cardHolderName.trim());
        card.setCardNumber(maskCard(cardNumber)); // mask for security
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setCvv("***"); // never store raw CVV
        card.setAmount(0);
        card.setStatus("ACTIVE");
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(card);
    }

    // READ - Get all saved cards for a user
    public List<Payment> getCards(Long userId) {
        return paymentRepository.findAll().stream()
                .filter(p -> userId.equals(p.getCustomerId()) && "CARD".equals(p.getType()))
                .toList();
    }

    // READ - Get all payment transactions for a user
    public List<Payment> getTransactions(Long userId) {
        return paymentRepository.findAll().stream()
                .filter(p -> userId.equals(p.getCustomerId()) && "TRANSACTION".equals(p.getType()))
                .toList();
    }

    // READ - Find payment by ID
    public Optional<Payment> findById(Long id) {
        return paymentRepository.findById(id);
    }

    // UPDATE - Buyer updates a saved card's details
    public Payment updateCard(Long userId, Long cardId, String cardHolderName,
                               String cardNumber, String expiryMonth, String expiryYear, String cvv) {
        Payment card = paymentRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found."));
        if (!card.getCustomerId().equals(userId))
            throw new IllegalArgumentException("This card does not belong to you.");
        if (cardHolderName != null && !cardHolderName.isBlank())
            card.setCardHolderName(cardHolderName.trim());
        if (cardNumber != null && !cardNumber.isBlank())
            card.setCardNumber(maskCard(cardNumber));
        if (expiryMonth != null && !expiryMonth.isBlank())
            card.setExpiryMonth(expiryMonth);
        if (expiryYear != null && !expiryYear.isBlank())
            card.setExpiryYear(expiryYear);
        card.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.update(card);
    }

    // CREATE - Process payment for a confirmed booking
    public Payment payForBooking(Long userId, Long bookingId, Long cardId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!booking.getCustomerId().equals(userId))
            throw new IllegalArgumentException("This booking does not belong to you.");
        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus()))
            throw new IllegalArgumentException("Booking must be confirmed before payment.");
        if (booking.isPaid())
            throw new IllegalArgumentException("This booking has already been paid.");

        // Validate card belongs to user
        if (cardId != null) {
            Payment card = paymentRepository.findById(cardId).orElse(null);
            if (card == null || !card.getCustomerId().equals(userId))
                throw new IllegalArgumentException("Invalid card selected.");
        }

        Payment tx = new Payment();
        tx.setCustomerId(userId);
        tx.setBookingId(bookingId);
        tx.setType("TRANSACTION");
        tx.setAmount(booking.getOfferAmount());
        tx.setStatus("COMPLETED");
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());

        // Mark booking as paid
        booking.setPaid(true);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.update(booking);

        return paymentRepository.save(tx);
    }

    // DELETE - Buyer removes a saved card
    public void deleteCard(Long userId, Long cardId) {
        Payment card = paymentRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found."));
        if (!card.getCustomerId().equals(userId))
            throw new IllegalArgumentException("This card does not belong to you.");
        paymentRepository.deleteById(cardId);
    }

    // ═══ Private helpers ═══

    private void validateCard(String number, String month, String year, String cvv) {
        if (number == null || number.replaceAll("\\s", "").length() < 13)
            throw new IllegalArgumentException("Invalid card number.");
        if (month == null || month.isBlank() || year == null || year.isBlank())
            throw new IllegalArgumentException("Card expiry date is required.");
        if (cvv == null || cvv.trim().length() < 3)
            throw new IllegalArgumentException("Invalid CVV.");
    }

    private String maskCard(String number) {
        String clean = number.replaceAll("\\s", "");
        if (clean.length() < 4) return "****";
        return "**** **** **** " + clean.substring(clean.length() - 4);
    }
}
