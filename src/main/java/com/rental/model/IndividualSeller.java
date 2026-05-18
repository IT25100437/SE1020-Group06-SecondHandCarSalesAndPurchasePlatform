package com.rental.model;

// Component 05: Seller & Dealer Management
// Inheritance: IndividualSeller extends Seller
public class IndividualSeller extends Seller {
    private String nationalId;

    public IndividualSeller() { setType("individual"); }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    // Polymorphism: different badge
    @Override
    public String getSellerTypeBadge() { return "Individual Seller"; }
}
