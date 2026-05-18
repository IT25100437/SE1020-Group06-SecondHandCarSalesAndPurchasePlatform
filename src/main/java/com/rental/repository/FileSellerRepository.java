package com.rental.repository;

import com.rental.model.Seller;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.*;

// Component 05: Seller & Dealer Management - File-based Repository
@Repository
public class FileSellerRepository implements SellerRepository {

    private static final String DELIM = "|";
    private final Path filePath;
    private final FileHandler fileHandler;

    public FileSellerRepository(
            @Value("${app.data.sellers-file:src/main/resources/data/sellers.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    @Override
    public synchronized Seller save(Seller seller) {
        List<Seller> all = findAll();
        if (seller.getId() == null) {
            seller.setId(nextId(all));
            all.add(seller);
        } else {
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId().equals(seller.getId())) {
                    all.set(i, seller); found = true; break;
                }
            }
            if (!found) all.add(seller);
        }
        writeAll(all);
        return seller;
    }

    @Override
    public synchronized Optional<Seller> findById(Long id) {
        return findAll().stream().filter(s -> id.equals(s.getId())).findFirst();
    }

    @Override
    public synchronized Optional<Seller> findByEmail(String email) {
        String norm = email == null ? "" : email.trim().toLowerCase();
        return findAll().stream()
                .filter(s -> s.getEmail() != null && s.getEmail().trim().toLowerCase().equals(norm))
                .findFirst();
    }

    @Override
    public synchronized List<Seller> findAll() {
        List<Seller> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) list.add(parseLine(line));
        }
        return list;
    }

    @Override
    public synchronized List<Seller> findByName(String name) {
        String q = name == null ? "" : name.trim().toLowerCase();
        if (q.isBlank()) return findAll();
        return findAll().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(q)).toList();
    }

    @Override
    public synchronized List<Seller> findByLocation(String location) {
        String q = location == null ? "" : location.trim().toLowerCase();
        if (q.isBlank()) return findAll();
        return findAll().stream()
                .filter(s -> s.getLocation() != null && s.getLocation().toLowerCase().contains(q)).toList();
    }

    @Override
    public synchronized void deleteById(Long id) {
        List<Seller> all = findAll();
        if (all.removeIf(s -> id.equals(s.getId()))) writeAll(all);
    }

    private Long nextId(List<Seller> list) {
        return list.stream().map(Seller::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<Seller> list) {
        fileHandler.writeLines(filePath, list.stream().map(this::toLine).toList());
    }

    // id|name|contact|email|password|location|type|image|carIdsCsv|isApproved
    private String toLine(Seller s) {
        String carIds = (s.getCarIds() == null || s.getCarIds().isEmpty()) ? "" :
                s.getCarIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return s.getId() + DELIM + esc(s.getName()) + DELIM + esc(s.getContact()) + DELIM
                + esc(s.getEmail()) + DELIM + esc(s.getPassword()) + DELIM
                + esc(s.getLocation()) + DELIM + esc(s.getType()) + DELIM
                + esc(s.getImage()) + DELIM + esc(carIds) + DELIM + s.isApproved();
    }

    private Seller parseLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != 10) throw new IllegalStateException("Corrupted seller row: " + line);
        Seller s = new Seller();
        s.setId(Long.parseLong(p[0]));
        s.setName(unesc(p[1]));
        s.setContact(unesc(p[2]));
        s.setEmail(unesc(p[3]));
        s.setPassword(unesc(p[4]));
        s.setLocation(unesc(p[5]));
        s.setType(unesc(p[6]));
        s.setImage(unesc(p[7]));
        String carIdsCsv = unesc(p[8]);
        List<Long> ids = new ArrayList<>();
        if (!carIdsCsv.isBlank()) {
            for (String t : carIdsCsv.split(",")) {
                try { ids.add(Long.parseLong(t.trim())); } catch (Exception ignored) {}
            }
        }
        s.setCarIds(ids);
        s.setApproved(Boolean.parseBoolean(p[9]));
        return s;
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
