package com.rental.controller;

import com.rental.model.Vehicle;
import com.rental.service.VehicleService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Component 02: Car Listing Management Controller
// Full CRUD for vehicle listings; uses LinkedList + MergeSort in service layer
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private static final String SESSION_ROLE = "AUTH_ROLE";

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    // CREATE - Seller submits a new car listing
    @PostMapping
    public ResponseEntity<?> create(@RequestBody VehicleRequest req, HttpSession session) {
        Long sellerId = userId(session);
        if (sellerId == null || !isSeller(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only sellers can add vehicles."));
        try {
            Vehicle v = vehicleService.createVehicle(sellerId, req.brand(), req.model(), req.year(),
                    req.price(), req.mileage(), req.imageUrl(), req.description(), req.type());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Listing submitted for admin approval.", "vehicle", vehicleView(v)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // READ - Browse approved vehicles (publicly accessible)
    // Internally uses VehicleLinkedList + MergeSort
    @GetMapping
    public ResponseEntity<?> browse(@RequestParam(required = false) String query,
                                    @RequestParam(required = false, defaultValue = "price") String sortBy,
                                    @RequestParam(required = false, defaultValue = "true") boolean asc) {
        List<Map<String, Object>> vehicles = vehicleService.getApprovedVehicles(query, sortBy, asc)
                .stream().map(this::vehicleView).toList();
        return ResponseEntity.ok(Map.of("vehicles", vehicles, "count", vehicles.size()));
    }

    // READ - Get a single approved vehicle by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        Vehicle v = vehicleService.findById(id).orElse(null);
        if (v == null || !("APPROVED".equalsIgnoreCase(v.getStatus()) || "SOLD".equalsIgnoreCase(v.getStatus())))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Vehicle not found."));
        return ResponseEntity.ok(Map.of("vehicle", vehicleView(v)));
    }

    // READ - Seller views their own listings
    @GetMapping("/mine")
    public ResponseEntity<?> mine(HttpSession session) {
        Long sellerId = userId(session);
        if (sellerId == null || !isSeller(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Sellers only."));
        return ResponseEntity.ok(Map.of("vehicles",
                vehicleService.getVehiclesBySeller(sellerId).stream().map(this::vehicleView).toList()));
    }

    // READ - Admin views pending listings
    @GetMapping("/admin/pending")
    public ResponseEntity<?> pending(HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin only."));
        return ResponseEntity.ok(Map.of("vehicles",
                vehicleService.getPendingVehicles().stream().map(this::vehicleView).toList()));
    }

    // UPDATE - Seller edits their own listing (re-submits for admin approval)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody VehicleRequest req,
                                    HttpSession session) {
        Long sellerId = userId(session);
        if (sellerId == null || !isSeller(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Sellers only."));
        try {
            Vehicle v = vehicleService.updateVehicle(sellerId, id, req.brand(), req.model(),
                    req.year(), req.price(), req.mileage(), req.imageUrl(), req.description(), req.type());
            return ResponseEntity.ok(Map.of("message", "Listing updated and re-submitted for approval.", "vehicle", vehicleView(v)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // UPDATE - Admin approves a listing
    @PostMapping("/admin/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin only."));
        try {
            return ResponseEntity.ok(Map.of("message", "Vehicle approved.", "vehicle", vehicleView(vehicleService.approveVehicle(id))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // UPDATE - Admin rejects a listing
    @PostMapping("/admin/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin only."));
        try {
            return ResponseEntity.ok(Map.of("message", "Vehicle rejected.", "vehicle", vehicleView(vehicleService.rejectVehicle(id))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Seller removes their own listing
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        Long sellerId = userId(session);
        if (sellerId == null || !isSeller(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Sellers only."));
        try {
            vehicleService.deleteVehicle(sellerId, id);
            return ResponseEntity.ok(Map.of("message", "Vehicle listing removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DELETE - Admin removes any listing (sold, expired, fraudulent)
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> adminDelete(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin only."));
        try {
            vehicleService.deleteVehicleByAdmin(id);
            return ResponseEntity.ok(Map.of("message", "Vehicle removed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ═══ Helpers ═══

    private Map<String, Object> vehicleView(Vehicle v) {
        String img = (v.getImages() == null || v.getImages().isEmpty()) ? "" : v.getImages().get(0);
        return Map.ofEntries(
                Map.entry("id", v.getId()),
                Map.entry("sellerId", v.getSellerId()),
                Map.entry("brand", v.getBrand() == null ? "" : v.getBrand()),
                Map.entry("model", v.getModel() == null ? "" : v.getModel()),
                Map.entry("title", v.getTitle()),
                Map.entry("year", v.getYear()),
                Map.entry("price", v.getPrice()),
                Map.entry("mileage", v.getMileage()),
                Map.entry("imageUrl", img),
                Map.entry("description", v.getDescription() == null ? "" : v.getDescription()),
                Map.entry("status", v.getStatus() == null ? "" : v.getStatus()),
                Map.entry("type", v.getType() == null ? "used" : v.getType()),
                Map.entry("typeBadge", v.getTypeBadge()),
                Map.entry("createdAt", v.getCreatedAt() == null ? "" : v.getCreatedAt().toString()));
    }

    private Long userId(HttpSession s) {
        Object r = s.getAttribute(SESSION_USER_ID);
        if (r instanceof Long l) return l;
        if (r instanceof Integer i) return i.longValue();
        return null;
    }

    private boolean isSeller(HttpSession s) {
        String role = (String) s.getAttribute(SESSION_ROLE);
        return "SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role);
    }

    private boolean isAdmin(HttpSession s) {
        String role = (String) s.getAttribute(SESSION_ROLE);
        return "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
    }

    public record VehicleRequest(String brand, String model, int year, double price,
                                  int mileage, String imageUrl, String description, String type) {}
}
