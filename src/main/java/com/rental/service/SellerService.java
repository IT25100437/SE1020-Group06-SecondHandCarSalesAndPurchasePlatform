package com.rental.service;

import com.rental.model.Seller;
import com.rental.repository.SellerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Component 05: Seller & Dealer Management
@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    // CREATE - Register seller (AUTO-APPROVED — only vehicle listings need admin approval)
    public Seller registerSeller(Seller seller) {
        if (seller.getName() == null || seller.getName().isBlank())
            throw new IllegalArgumentException("Name is required.");
        if (seller.getEmail() == null || seller.getEmail().isBlank())
            throw new IllegalArgumentException("Email is required.");
        if (seller.getPassword() == null || seller.getPassword().isBlank())
            throw new IllegalArgumentException("Password is required.");
        if (sellerRepository.findByEmail(seller.getEmail()).isPresent())
            throw new IllegalArgumentException("Email already registered.");
        seller.setEmail(seller.getEmail().trim().toLowerCase());
        seller.setApproved(true); // FIX: auto-approve accounts; only listings need approval
        return sellerRepository.save(seller);
    }

    public List<Seller> getAllSellers() { return sellerRepository.findAll(); }

    // FIX: return ALL sellers (approved + pending) so admin can see everyone
    public List<Seller> getPendingSellers() {
        return sellerRepository.findAll().stream()
                .filter(s -> !s.isApproved()).collect(Collectors.toList());
    }

    public List<Seller> getApprovedSellers() {
        return sellerRepository.findAll().stream()
                .filter(Seller::isApproved).collect(Collectors.toList());
    }

    public Optional<Seller> findById(Long id) { return sellerRepository.findById(id); }
    public Optional<Seller> findByEmail(String email) { return sellerRepository.findByEmail(email); }

    // FIX: search returns ALL sellers (approved + unapproved) so buyers can see everyone
    public List<Seller> searchSellers(String name, String location) {
        List<Seller> all = sellerRepository.findAll();
        if ((name == null || name.isBlank()) && (location == null || location.isBlank()))
            return all;
        String qName = name == null ? "" : name.trim().toLowerCase();
        String qLoc  = location == null ? "" : location.trim().toLowerCase();
        return all.stream().filter(s -> {
            boolean nameMatch = qName.isEmpty() || (s.getName() != null && s.getName().toLowerCase().contains(qName));
            boolean locMatch  = qLoc.isEmpty()  || (s.getLocation() != null && s.getLocation().toLowerCase().contains(qLoc));
            return nameMatch && locMatch;
        }).collect(Collectors.toList());
    }

    public Seller updateSeller(Long id, String name, String contact, String location, String image, String email) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        if (name != null && !name.isBlank()) seller.setName(name.trim());
        if (contact != null && !contact.isBlank()) seller.setContact(contact.trim());
        if (location != null && !location.isBlank()) seller.setLocation(location.trim());
        if (image != null && !image.isBlank()) seller.setImage(image.trim());
        if (email != null && !email.isBlank()) {
            String normEmail = email.trim().toLowerCase();
            java.util.Optional<com.rental.model.Seller> existing = sellerRepository.findByEmail(normEmail);
            if (existing.isPresent() && !existing.get().getId().equals(id))
                throw new IllegalArgumentException("Email already in use by another seller.");
            seller.setEmail(normEmail);
        }
        return sellerRepository.save(seller);
    }

    public Seller updatePassword(Long id, String oldPassword, String newPassword) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        if (!seller.getPassword().equals(oldPassword))
            throw new IllegalArgumentException("Current password is incorrect.");
        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        seller.setPassword(newPassword);
        return sellerRepository.save(seller);
    }

    public Seller approveSeller(Long id) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        seller.setApproved(true);
        return sellerRepository.save(seller);
    }

    public Seller rejectSeller(Long id) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        seller.setApproved(false);
        return sellerRepository.save(seller);
    }

    // Backward compat overload
    public Seller updateSeller(Long id, String name, String contact, String location, String image) {
        return updateSeller(id, name, contact, location, image, null);
    }

    public void deleteSeller(Long id) {
        sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found."));
        sellerRepository.deleteById(id);
    }
}
