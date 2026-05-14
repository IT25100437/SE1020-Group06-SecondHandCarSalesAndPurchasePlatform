package com.rental.model;

import java.time.LocalDateTime;
import java.util.List;

// Component 02: Car Listing Management
// Encapsulation: vehicle details stored securely
public class Vehicle {
    private Long id;
    private Long sellerId;
    private String brand;
    private String model;
    private double price;
    private int year;
    private int mileage;
    private List<String> images;
    private String description;
    private String status; // PENDING, APPROVED, REJECTED, SOLD
    private String type;   // used, certified
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Vehicle() {}

    public Vehicle(Long id, Long sellerId, String brand, String model, double price,
                   int year, int mileage, List<String> images, String description,
                   String status, String type, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.year = year;
        this.mileage = mileage;
        this.images = images;
        this.description = description;
        this.status = status;
        this.type = type;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Polymorphism: subclasses override badge
    public String getTypeBadge() { return "Used"; }

    // Compatibility helper
    public String getTitle() {
        return ((brand == null ? "" : brand) + " " + (model == null ? "" : model)).trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { this.mileage = mileage; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
