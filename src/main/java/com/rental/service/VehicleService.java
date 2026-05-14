package com.rental.service;

import com.rental.model.Vehicle;
import com.rental.repository.VehicleRepository;
import com.rental.util.MergeSort;
import com.rental.util.VehicleLinkedList;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Component 02: Car Listing Management - Service layer
// Uses VehicleLinkedList (custom data structure) and MergeSort (custom algorithm)
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // CREATE - Seller adds a new vehicle listing
    public Vehicle createVehicle(Long sellerId, String brand, String model, int year,
                                  double price, int mileage, String imageUrl,
                                  String description, String type) {
        if (brand == null || brand.isBlank()) throw new IllegalArgumentException("Brand is required.");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Model is required.");
        if (price <= 0) throw new IllegalArgumentException("Price must be greater than 0.");
        if (year < 1900 || year > LocalDateTime.now().getYear() + 1)
            throw new IllegalArgumentException("Please enter a valid year.");

        Vehicle v = new Vehicle();
        v.setSellerId(sellerId);
        v.setBrand(brand.trim());
        v.setModel(model.trim());
        v.setYear(year);
        v.setPrice(price);
        v.setMileage(mileage < 0 ? 0 : mileage);
        v.setImages(imageUrl == null || imageUrl.isBlank() ? List.of() : List.of(imageUrl.trim()));
        v.setDescription(description == null ? "" : description.trim());
        v.setStatus("PENDING"); // must be approved by admin before visible
        v.setType(type == null || type.isBlank() ? "used" : type.trim().toLowerCase());
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.save(v);
    }

    // READ - Get approved vehicles, loaded into LinkedList, sorted using MergeSort
    // This is where the data structure and algorithm are demonstrated
    public List<Vehicle> getApprovedVehicles(String query, String sortBy, boolean ascending) {
        // Step 1: Load all approved vehicles into our custom LinkedList
        VehicleLinkedList linkedList = new VehicleLinkedList();
        vehicleRepository.findAll().stream()
                .filter(v -> "APPROVED".equalsIgnoreCase(v.getStatus()))
                .forEach(linkedList::add);

        // Step 2: Search within linked list (O(n) traversal)
        List<Vehicle> results = (query != null && !query.isBlank())
                ? linkedList.search(query)
                : linkedList.toList();

        // Step 3: Sort using our custom Merge Sort algorithm
        if ("year".equalsIgnoreCase(sortBy)) {
            return MergeSort.sortByYear(results);
        }
        return MergeSort.sortByPrice(results, ascending);
    }

    // READ - Get all vehicles regardless of status (admin use)
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    // READ - Get all vehicles for a specific seller
    public List<Vehicle> getVehiclesBySeller(Long sellerId) {
        VehicleLinkedList linkedList = new VehicleLinkedList();
        vehicleRepository.findAll().stream()
                .filter(v -> sellerId.equals(v.getSellerId()))
                .forEach(linkedList::add);
        return linkedList.toList();
    }

    // READ - Get vehicles with PENDING status (admin approval queue)
    public List<Vehicle> getPendingVehicles() {
        VehicleLinkedList linkedList = new VehicleLinkedList();
        vehicleRepository.findAll().stream()
                .filter(v -> "PENDING".equalsIgnoreCase(v.getStatus()))
                .forEach(linkedList::add);
        return linkedList.toList();
    }

    // READ - Find a single vehicle by ID
    public Optional<Vehicle> findById(Long id) {
        return vehicleRepository.findById(id);
    }

    // UPDATE - Seller edits their own vehicle listing (re-submits for approval)
    public Vehicle updateVehicle(Long sellerId, Long vehicleId, String brand, String model,
                                  int year, double price, int mileage, String imageUrl,
                                  String description, String type) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        if (!v.getSellerId().equals(sellerId))
            throw new IllegalArgumentException("You can only edit your own listings.");
        if (brand != null && !brand.isBlank()) v.setBrand(brand.trim());
        if (model != null && !model.isBlank()) v.setModel(model.trim());
        if (year > 1900) v.setYear(year);
        if (price > 0) v.setPrice(price);
        if (mileage >= 0) v.setMileage(mileage);
        if (imageUrl != null && !imageUrl.isBlank()) v.setImages(List.of(imageUrl.trim()));
        if (description != null) v.setDescription(description.trim());
        if (type != null && !type.isBlank()) v.setType(type.trim().toLowerCase());
        v.setStatus("PENDING"); // re-submit for admin approval after edit
        v.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.update(v);
    }

    // UPDATE - Admin approves a vehicle listing
    public Vehicle approveVehicle(Long vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        v.setStatus("APPROVED");
        v.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.update(v);
    }

    // UPDATE - Admin rejects a vehicle listing
    public Vehicle rejectVehicle(Long vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        v.setStatus("REJECTED");
        v.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.update(v);
    }

    // UPDATE - Mark vehicle as sold (triggered when sale is completed)
    public Vehicle markAsSold(Long vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        v.setStatus("SOLD");
        v.setUpdatedAt(LocalDateTime.now());
        return vehicleRepository.update(v);
    }

    // DELETE - Seller removes their own vehicle listing
    public void deleteVehicle(Long sellerId, Long vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
        if (!v.getSellerId().equals(sellerId))
            throw new IllegalArgumentException("You can only delete your own listings.");
        vehicleRepository.deleteById(vehicleId);
    }

    // DELETE - Admin removes any vehicle listing (expired, fraudulent, etc.)
    public void deleteVehicleByAdmin(Long vehicleId) {
        if (!vehicleRepository.deleteById(vehicleId))
            throw new IllegalArgumentException("Vehicle not found.");
    }
}
