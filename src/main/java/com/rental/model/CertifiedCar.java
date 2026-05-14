package com.rental.model;

// Component 02: Car Listing Management
// Inheritance: CertifiedCar extends Vehicle
public class CertifiedCar extends Vehicle {
    private String warranty; // e.g. "2 Year Warranty"

    public CertifiedCar() { setType("certified"); }

    public String getWarranty() { return warranty; }
    public void setWarranty(String warranty) { this.warranty = warranty; }

    // Polymorphism: different badge from UsedCar
    @Override
    public String getTypeBadge() { return "Certified - " + (warranty != null ? warranty : ""); }
}
