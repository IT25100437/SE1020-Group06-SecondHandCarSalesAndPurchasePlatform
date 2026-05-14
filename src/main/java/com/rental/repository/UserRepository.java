package com.rental.repository;

import com.rental.model.User;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 01: User Management - Repository layer
@Repository
public class UserRepository {

    private static final String DELIM = "|";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public UserRepository(
            @Value("${app.data.users-file:src/main/resources/data/users.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    public synchronized List<User> findAll() {
        List<User> users = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line != null && !line.isBlank()) users.add(parseLine(line));
        }
        return users;
    }

    public synchronized Optional<User> findById(Long id) {
        return findAll().stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    public synchronized Optional<User> findByEmail(String email) {
        String norm = email == null ? "" : email.trim().toLowerCase();
        return findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(norm))
                .findFirst();
    }

    public synchronized User save(User user) {
        List<User> all = findAll();
        if (user.getId() == null) user.setId(nextId(all));
        all.add(user);
        writeAll(all);
        return user;
    }

    public synchronized User update(User user) {
        List<User> all = findAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(user.getId())) {
                all.set(i, user);
                writeAll(all);
                return user;
            }
        }
        throw new IllegalArgumentException("User not found.");
    }

    public synchronized boolean deleteById(Long id) {
        List<User> all = findAll();
        boolean removed = all.removeIf(u -> u.getId().equals(id));
        if (removed) writeAll(all);
        return removed;
    }

    private Long nextId(List<User> users) {
        return users.stream().map(User::getId).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private void writeAll(List<User> users) {
        fileHandler.writeLines(filePath, users.stream().map(this::toLine).toList());
    }

    private String toLine(User u) {
        return u.getId() + DELIM + esc(u.getName()) + DELIM + esc(u.getEmail()) + DELIM
                + esc(u.getPhone()) + DELIM + esc(u.getAddress()) + DELIM
                + esc(u.getRole()) + DELIM + esc(u.getPasswordHash()) + DELIM
                + (u.getCreatedAt() == null ? "" : FMT.format(u.getCreatedAt())) + DELIM
                + (u.getUpdatedAt() == null ? "" : FMT.format(u.getUpdatedAt()));
    }

    private User parseLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 9) throw new IllegalStateException("Corrupted user row: " + line);
        return new User(Long.parseLong(p[0]), unesc(p[1]), unesc(p[2]), unesc(p[3]),
                unesc(p[4]), unesc(p[5]), unesc(p[6]), parsedt(p[7]), parsedt(p[8]));
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
