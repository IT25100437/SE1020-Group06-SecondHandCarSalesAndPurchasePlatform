package com.rental.repository;

import com.rental.model.Booking;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Component 03 & 05 — Booking Repository (shared data file)
 * Reads and writes booking records to bookings.txt using pipe-delimited format.
 * Handles both old format (11 fields) and new format (13 fields with counter-offer).
 */
@Repository
public class BookingRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public BookingRepository(
            @Value("${app.data.bookings-file:src/main/resources/data/bookings.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Booking> findAll() {
        List<Booking> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) {
                try { list.add(parseLine(line)); } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public synchronized Optional<Booking> findById(Long id) {
        return findAll().stream().filter(b -> b.getId().equals(id)).findFirst();
    }

    public synchronized Booking save(Booking booking) {
        List<Booking> all = findAll();
        if (booking.getId() == null) booking.setId(nextId(all));
        all.add(booking);
        writeAll(all);
        return booking;
    }

    public synchronized Booking update(Booking booking) {
        List<Booking> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(booking.getId())) {
                all.set(i, booking); writeAll(all); return booking;
            }
        }
        throw new IllegalArgumentException("Booking not found.");
    }

    public synchronized boolean deleteById(Long id) {
        List<Booking> all = findAll();
        boolean removed = all.removeIf(b -> b.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<Booking> list) {
        return list.stream().map(Booking::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Booking> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // Format: id|vehicleId|renterId|customerId|offerAmount|offerMessage|
    //         counterAmount|sellerNote|totalAmount|status|paid|createdAt|updatedAt
    private String toLine(Booking b) {
        return b.getId() + DELIM + b.getVehicleId() + DELIM + b.getRenterId() + DELIM
                + b.getCustomerId() + DELIM + b.getOfferAmount() + DELIM
                + esc(b.getOfferMessage()) + DELIM
                + b.getCounterAmount() + DELIM
                + esc(b.getSellerNote()) + DELIM
                + b.getTotalAmount() + DELIM
                + esc(b.getStatus()) + DELIM + b.isPaid() + DELIM
                + (b.getCreatedAt() == null ? "" : FMT.format(b.getCreatedAt())) + DELIM
                + (b.getUpdatedAt() == null ? "" : FMT.format(b.getUpdatedAt()));
    }

    private Booking parseLine(String line) {
        String[] p = line.split("\\|", -1);
        Booking b = new Booking();
        b.setId(Long.parseLong(p[0].trim()));
        b.setVehicleId(Long.parseLong(p[1].trim()));
        b.setRenterId(Long.parseLong(p[2].trim()));
        b.setCustomerId(Long.parseLong(p[3].trim()));
        b.setOfferAmount(Double.parseDouble(p[4].trim()));
        if (p.length >= 13) {
            // New format with counter-offer fields
            b.setOfferMessage(unesc(p[5]));
            b.setCounterAmount(Double.parseDouble(p[6].trim()));
            b.setSellerNote(unesc(p[7]));
            b.setTotalAmount(Double.parseDouble(p[8].trim()));
            b.setStatus(unesc(p[9]));
            b.setPaid(Boolean.parseBoolean(p[10].trim()));
            b.setCreatedAt(parsedt(p[11]));
            b.setUpdatedAt(parsedt(p[12]));
        } else if (p.length >= 11) {
            // Old format without counter-offer — backward compatible
            b.setOfferMessage(unesc(p[5]));
            b.setCounterAmount(0);
            b.setSellerNote("");
            b.setTotalAmount(Double.parseDouble(p[6].trim()));
            b.setStatus(unesc(p[7]));
            b.setPaid(Boolean.parseBoolean(p[8].trim()));
            b.setCreatedAt(parsedt(p[9]));
            b.setUpdatedAt(parsedt(p[10]));
        }
        return b;
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
