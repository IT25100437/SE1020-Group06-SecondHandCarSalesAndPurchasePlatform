package com.rental.repository;

import com.rental.model.Vehicle;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 02: Car Listing Management - Repository layer
@Repository
public class VehicleRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public VehicleRepository(
            @Value("${app.data.vehicles-file:src/main/resources/data/vehicles.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Vehicle> findAll() {
        List<Vehicle> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) list.add(parseLine(line));
        }
        return list;
    }

    public synchronized Optional<Vehicle> findById(Long id) {
        return findAll().stream().filter(v -> v.getId().equals(id)).findFirst();
    }

    public synchronized Vehicle save(Vehicle vehicle) {
        List<Vehicle> all = findAll();
        if (vehicle.getId() == null) vehicle.setId(nextId(all));
        all.add(vehicle);
        writeAll(all);
        return vehicle;
    }

    public synchronized Vehicle update(Vehicle vehicle) {
        List<Vehicle> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(vehicle.getId())) {
                all.set(i, vehicle);
                writeAll(all);
                return vehicle;
            }
        }
        throw new IllegalArgumentException("Vehicle not found.");
    }

    public synchronized boolean deleteById(Long id) {
        List<Vehicle> all = findAll();
        boolean removed = all.removeIf(v -> v.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<Vehicle> list) {
        return list.stream().map(Vehicle::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Vehicle> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // Format: id|sellerId|brand|model|year|price|mileage|description|imageUrl|status|type|createdAt|updatedAt
    private String toLine(Vehicle v) {
        String img = (v.getImages() == null || v.getImages().isEmpty()) ? "" : v.getImages().get(0);
        return v.getId() + DELIM + v.getSellerId() + DELIM + esc(v.getBrand()) + DELIM
                + esc(v.getModel()) + DELIM + v.getYear() + DELIM + v.getPrice() + DELIM
                + v.getMileage() + DELIM + esc(v.getDescription()) + DELIM + esc(img) + DELIM
                + esc(v.getStatus()) + DELIM + esc(v.getType()) + DELIM
                + (v.getCreatedAt() == null ? "" : FMT.format(v.getCreatedAt())) + DELIM
                + (v.getUpdatedAt() == null ? "" : FMT.format(v.getUpdatedAt()));
    }

    private Vehicle parseLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 13) throw new IllegalStateException("Corrupted vehicle row: " + line);
        Vehicle v = new Vehicle();
        v.setId(Long.parseLong(p[0]));
        v.setSellerId(Long.parseLong(p[1]));
        v.setBrand(unesc(p[2]));
        v.setModel(unesc(p[3]));
        v.setYear(Integer.parseInt(p[4]));
        v.setPrice(Double.parseDouble(p[5]));
        v.setMileage(Integer.parseInt(p[6]));
        v.setDescription(unesc(p[7]));
        String img = unesc(p[8]);
        v.setImages(img.isBlank() ? List.of() : List.of(img));
        v.setStatus(unesc(p[9]));
        v.setType(unesc(p[10]));
        v.setCreatedAt(parsedt(p[11]));
        v.setUpdatedAt(parsedt(p[12]));
        return v;
    }

    private LocalDateTime parsedt(String v) {
        return (v == null || v.isBlank()) ? null : LocalDateTime.parse(v, FMT);
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private String unesc(String v) {
        if (v == null || v.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (char c : v.toCharArray()) {
            if (esc) { sb.append(c == 'n' ? '\n' : c); esc = false; }
            else if (c == '\\') esc = true;
            else sb.append(c);
        }
        return sb.toString();
    }
}
