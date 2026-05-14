package com.rental.model;

// Component 02: Car Listing Management
// Inheritance: UsedCar extends Vehicle
public class UsedCar extends Vehicle {
    private String condition; // Good, Fair, Excellent

    public UsedCar() { setType("used"); }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    // Polymorphism: different badge from CertifiedCar
    @Override
    public String getTypeBadge() { return "Used - " + (condition != null ? condition : ""); }
}
