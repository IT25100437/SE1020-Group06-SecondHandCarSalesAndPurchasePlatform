package com.rental.repository;

import com.rental.model.Payment;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 04: Purchase Management - Payment Repository
@Repository
public class PaymentRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public PaymentRepository(
            @Value("${app.data.payments-file:src/main/resources/data/payments.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Payment> findAll() {
        List<Payment> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) list.add(parseLine(line));
        }
        return list;
    }

    public synchronized Optional<Payment> findById(Long id) {
        return findAll().stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public synchronized Payment save(Payment payment) {
        List<Payment> all = findAll();
        if (payment.getId() == null) payment.setId(nextId(all));
        all.add(payment);
        writeAll(all);
        return payment;
    }

    public synchronized Payment update(Payment payment) {
        List<Payment> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(payment.getId())) {
                all.set(i, payment); writeAll(all); return payment;
            }
        }
        throw new IllegalArgumentException("Payment not found.");
    }

    public synchronized boolean deleteById(Long id) {
        List<Payment> all = findAll();
        boolean removed = all.removeIf(p -> p.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<Payment> list) {
        return list.stream().map(Payment::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Payment> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // id|customerId|bookingId|type|cardHolderName|cardNumber|expiryMonth|expiryYear|cvv|amount|status|createdAt|updatedAt
    private String toLine(Payment p) {
        return p.getId() + DELIM + p.getCustomerId() + DELIM + p.getBookingId() + DELIM
                + esc(p.getType()) + DELIM + esc(p.getCardHolderName()) + DELIM
                + esc(p.getCardNumber()) + DELIM + esc(p.getExpiryMonth()) + DELIM
                + esc(p.getExpiryYear()) + DELIM + esc(p.getCvv()) + DELIM
                + p.getAmount() + DELIM + esc(p.getStatus()) + DELIM
                + (p.getCreatedAt() == null ? "" : FMT.format(p.getCreatedAt())) + DELIM
                + (p.getUpdatedAt() == null ? "" : FMT.format(p.getUpdatedAt()));
    }

    private Payment parseLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 13) throw new IllegalStateException("Corrupted payment row: " + line);
        return new Payment(Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]),
                unesc(p[3]), unesc(p[4]), unesc(p[5]), unesc(p[6]), unesc(p[7]), unesc(p[8]),
                Double.parseDouble(p[9]), unesc(p[10]), parsedt(p[11]), parsedt(p[12]));
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
