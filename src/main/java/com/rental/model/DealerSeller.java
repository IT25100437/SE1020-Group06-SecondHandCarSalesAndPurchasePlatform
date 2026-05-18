package com.rental.model;

// Component 05: Seller & Dealer Management
// Inheritance: DealerSeller extends Seller
public class DealerSeller extends Seller {
    private String dealershipName;
    private String licenseNumber;

    public DealerSeller() { setType("dealer"); }

    public String getDealershipName() { return dealershipName; }
    public void setDealershipName(String dealershipName) { this.dealershipName = dealershipName; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    // Polymorphism: different badge
    @Override
    public String getSellerTypeBadge() { return "Dealer - " + (dealershipName != null ? dealershipName : ""); }
}
