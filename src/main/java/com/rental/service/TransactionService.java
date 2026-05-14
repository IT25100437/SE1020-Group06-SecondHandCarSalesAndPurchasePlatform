package com.rental.service;

import com.rental.model.Transaction;
import com.rental.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Component (Selling Management): Transaction Service - records completed sales
// Encapsulation: all transaction business logic is here
@Service
public class TransactionService {

    private final TransactionRepository repo;

    public TransactionService(TransactionRepository repo) {
        this.repo = repo;
    }

    // CREATE - Record a new sale transaction
    public Transaction create(Long vehicleId, Long sellerId, Long buyerId,
                               double amount, String paymentMethod) {
        if (amount <= 0) throw new IllegalArgumentException("Transaction amount must be greater than 0.");
        Transaction t = new Transaction();
        t.setVehicleId(vehicleId);
        t.setSellerId(sellerId);
        t.setBuyerId(buyerId);
        t.setAmount(amount);
        t.setStatus("COMPLETED");
        t.setPaymentMethod(paymentMethod == null ? "cash" : paymentMethod.trim());
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return repo.save(t);
    }

    // CREATE - Save a pre-built transaction object (used by BookingService)
    public Transaction create(Transaction t) {
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return repo.save(t);
    }

    // READ - Find a transaction by ID
    public Optional<Transaction> findById(Long id) {
        return repo.findById(id);
    }

    // READ - Get all purchase transactions for a buyer
    public List<Transaction> findByBuyer(Long buyerId) {
        return repo.findByBuyerId(buyerId);
    }

    // READ - Get all sale transactions for a seller
    public List<Transaction> findBySeller(Long sellerId) {
        return repo.findBySellerId(sellerId);
    }

    // READ - Get all transactions (admin use)
    public List<Transaction> findAll() {
        return repo.findAll();
    }

    // UPDATE - Update transaction status (e.g. PENDING → COMPLETED, or flag CANCELLED)
    public Transaction updateStatus(Long id, String newStatus) {
        Transaction t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));
        if (newStatus == null || newStatus.isBlank())
            throw new IllegalArgumentException("Status is required.");
        String status = newStatus.trim().toUpperCase();
        if (!status.equals("PENDING") && !status.equals("COMPLETED") && !status.equals("CANCELLED"))
            throw new IllegalArgumentException("Status must be PENDING, COMPLETED, or CANCELLED.");
        t.setStatus(status);
        t.setUpdatedAt(LocalDateTime.now());
        return repo.update(t);
    }

    // UPDATE - Update a full transaction object
    public Transaction update(Transaction t) {
        t.setUpdatedAt(LocalDateTime.now());
        return repo.update(t);
    }

    // DELETE - Remove an invalid or cancelled transaction
    public void delete(Long id) {
        if (!repo.deleteById(id))
            throw new IllegalArgumentException("Transaction not found.");
    }
}
