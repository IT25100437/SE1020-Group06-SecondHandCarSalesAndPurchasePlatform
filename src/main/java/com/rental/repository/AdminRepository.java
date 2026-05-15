package com.rental.repository;

import com.rental.model.Admin;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.*;

// Component 04: Admin Management - Repository layer
// Handles file read/write for admin accounts
@Repository
public class AdminRepository {

    private final Path filePath;
    private final FileHandler fileHandler;

    public AdminRepository(
            @Value("${app.data.admins-file:src/main/resources/data/admins.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<Admin> findAll() {
        List<Admin> list = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) {
                try { list.add(parseLine(line)); } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public synchronized Optional<Admin> findById(String adminId) {
        if (adminId == null) return Optional.empty();
        return findAll().stream()
                .filter(a -> adminId.equalsIgnoreCase(a.getAdminId()))
                .findFirst();
    }

    public synchronized Optional<Admin> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String norm = email.trim().toLowerCase();
        return findAll().stream()
                .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(norm))
                .findFirst();
    }

    public synchronized Admin save(Admin admin) {
        List<Admin> all = findAll();
        if (admin.getAdminId() == null || admin.getAdminId().isBlank())
            admin.setAdminId(nextId(all));
        all.add(admin);
        writeAll(all);
        return admin;
    }

    public synchronized Admin update(Admin admin) {
        List<Admin> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getAdminId().equalsIgnoreCase(admin.getAdminId())) {
                all.set(i, admin);
                writeAll(all);
                return admin;
            }
        }
        throw new IllegalArgumentException("Admin not found.");
    }

    public synchronized boolean deleteById(String adminId) {
        List<Admin> all = findAll();
        boolean removed = all.removeIf(a -> a.getAdminId().equalsIgnoreCase(adminId));
        if (removed) writeAll(all);
        return removed;
    }

    public synchronized long count() {
        return findAll().size();
    }

    // --- Private helpers ---

    private String nextId(List<Admin> admins) {
        int max = 0;
        for (Admin a : admins) {
            String id = a.getAdminId();
            if (id != null && id.toUpperCase().startsWith("A")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(1))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return String.format("A%03d", max + 1);
    }

    private void writeAll(List<Admin> admins) {
        fileHandler.writeLines(filePath, admins.stream().map(this::toLine).toList());
    }

    // Format: adminId,name,email,passwordHash,role
    private String toLine(Admin a) {
        return esc(a.getAdminId()) + "," + esc(a.getName()) + "," +
               esc(a.getEmail()) + "," + esc(a.getPasswordHash()) + "," + esc(a.getRole());
    }

    private Admin parseLine(String line) {
        String[] p = line.split(",", -1);
        if (p.length < 5) throw new IllegalStateException("Corrupted admin row: " + line);
        return new Admin(unesc(p[0]), unesc(p[1]), unesc(p[2]), unesc(p[3]), unesc(p[4]));
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace(",", "\\,").replace("\n", "\\n");
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
