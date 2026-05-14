package com.rental.model;

import java.time.LocalDateTime;

/**
 * Component 03 & 05: Booking model — shared data between Selling and Purchase Management.
 * Represents a purchase request from a buyer to a seller.
 *
 * OOP — Encapsulation: all fields private, only accessible via getters/setters.
 * The booking follows a strict state machine:
 *   PENDING → CONFIRMED → SOLD
 *   PENDING → COUNTERED → CONFIRMED (seller posts counter-offer, buyer accepts)
 *   PENDING → REJECTED
 */
public class Booking {
    private Long id;
    private Long vehicleId;
    private Long renterId;      // seller ID
    private Long customerId;    // buyer ID
    private double offerAmount;
    private String offerMessage;
    private double counterAmount; // seller's counter-offer price (Component 03)
    private String sellerNote;    // seller's note with counter-offer (Component 03)
    private double totalAmount;
    private String status; // PENDING, COUNTERED, CONFIRMED, REJECTED, SOLD
    private boolean paid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Booking() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public Long getRenterId() { return renterId; }
    public void setRenterId(Long renterId) { this.renterId = renterId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public double getOfferAmount() { return offerAmount; }
    public void setOfferAmount(double offerAmount) { this.offerAmount = offerAmount; }
    public String getOfferMessage() { return offerMessage; }
    public void setOfferMessage(String offerMessage) { this.offerMessage = offerMessage; }
    public double getCounterAmount() { return counterAmount; }
    public void setCounterAmount(double counterAmount) { this.counterAmount = counterAmount; }
    public String getSellerNote() { return sellerNote; }
    public void setSellerNote(String sellerNote) { this.sellerNote = sellerNote; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
