package com.rental.repository;

import com.rental.model.Transaction;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 03: Selling Management - Transaction Repository
@Repository
public class TransactionRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public TransactionRepository(
            @Value("${app.data.transactions-file:src/main/resources/data/transactions.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Transaction> findAll() {
        List<Transaction> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) list.add(parseLine(line));
        }
        return list;
    }

    public synchronized Optional<Transaction> findById(Long id) {
        return findAll().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public synchronized List<Transaction> findByBuyerId(Long buyerId) {
        return findAll().stream().filter(t -> buyerId.equals(t.getBuyerId())).toList();
    }

    public synchronized List<Transaction> findBySellerId(Long sellerId) {
        return findAll().stream().filter(t -> sellerId.equals(t.getSellerId())).toList();
    }

    public synchronized Transaction save(Transaction t) {
        List<Transaction> all = findAll();
        if (t.getId() == null) t.setId(nextId(all));
        all.add(t);
        writeAll(all);
        return t;
    }

    public synchronized Transaction update(Transaction t) {
        List<Transaction> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(t.getId())) {
                all.set(i, t); writeAll(all); return t;
            }
        }
        throw new IllegalArgumentException("Transaction not found.");
    }

    public synchronized boolean deleteById(Long id) {
        List<Transaction> all = findAll();
        boolean removed = all.removeIf(t -> t.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<Transaction> list) {
        return list.stream().map(Transaction::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Transaction> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // id|vehicleId|sellerId|buyerId|amount|status|paymentMethod|createdAt|updatedAt
    private String toLine(Transaction t) {
        return t.getId() + DELIM + t.getVehicleId() + DELIM + t.getSellerId() + DELIM
                + t.getBuyerId() + DELIM + t.getAmount() + DELIM + esc(t.getStatus()) + DELIM
                + esc(t.getPaymentMethod()) + DELIM
                + (t.getCreatedAt() == null ? "" : FMT.format(t.getCreatedAt())) + DELIM
                + (t.getUpdatedAt() == null ? "" : FMT.format(t.getUpdatedAt()));
    }

    private Transaction parseLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 9) throw new IllegalStateException("Corrupted transaction row: " + line);
        return new Transaction(Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]),
                Long.parseLong(p[3]), Double.parseDouble(p[4]), unesc(p[5]), unesc(p[6]),
                parsedt(p[7]), parsedt(p[8]));
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
