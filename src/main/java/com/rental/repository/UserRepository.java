package com.rental.repository;
//done
import com.rental.model.User;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Component 01: User Management - Repository layer
@Repository // Marks this class as a Spring Repository component
public class UserRepository {

    private static final String DELIM = "|"; // Delimiter used to separate values in the file
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // Standard date-time format

    private final Path filePath; // Stores the path of the users file
    private final FileHandler fileHandler; // FileHandler object for file operations

    // Constructor for dependency injection
    public UserRepository(
            @Value("${app.data.users-file:src/main/resources/data/users.txt}") String file, // Reads file path from application properties
            FileHandler fileHandler) { // Injects FileHandler dependency
        this.filePath = Path.of(file); // Converts file string path into Path object
        this.fileHandler = fileHandler; // Assigns FileHandler instance
        this.fileHandler.ensureFileExists(this.filePath); // Creates the file if it does not exist
    }

    // Returns all users from the file
    public synchronized List<User> findAll() {
        List<User> users = new ArrayList<>(); // Creates empty user list

        // Reads each line from the file
        for (String line : fileHandler.readLines(filePath)) {

            // Checks if line is not null or empty
            if (line != null && !line.isBlank())

                users.add(parseLine(line)); // Converts line into User object and adds to list
        }

        return users; // Returns all users
    }

    // Finds a user by ID
    public synchronized Optional<User> findById(Long id) {

        // Searches all users and returns matching ID
        return findAll().stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    // Finds a user by email
    public synchronized Optional<User> findByEmail(String email) {

        // Converts email to lowercase and removes spaces
        String norm = email == null ? "" : email.trim().toLowerCase();

        // Searches for matching email ignoring case sensitivity
        return findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(norm))
                .findFirst();
    }

    // Saves a new user
    public synchronized User save(User user) {

        List<User> all = findAll(); // Gets all existing users

        // Generates new ID if user ID is null
        if (user.getId() == null)
            user.setId(nextId(all));

        all.add(user); // Adds new user to list

        writeAll(all); // Writes updated list back to file

        return user; // Returns saved user
    }

    // Updates an existing user
    public synchronized User update(User user) {

        List<User> all = findAll(); // Loads all users

        // Loops through user list
        for (int i = 0; i < all.size(); i++) {

            // Checks if current user ID matches
            if (all.get(i).getId().equals(user.getId())) {

                all.set(i, user); // Replaces old user with updated user

                writeAll(all); // Saves updated list to file

                return user; // Returns updated user
            }
        }

        // Throws exception if user not found
        throw new IllegalArgumentException("User not found.");
    }

    // Deletes a user by ID
    public synchronized boolean deleteById(Long id) {

        List<User> all = findAll(); // Gets all users

        // Removes user if ID matches
        boolean removed = all.removeIf(u -> u.getId().equals(id));

        // Updates file only if user was removed
        if (removed)
            writeAll(all);

        return removed; // Returns true if deleted successfully
    }

    // Generates next available ID
    private Long nextId(List<User> users) {

        return users.stream() // Converts list into stream
                .map(User::getId) // Gets all user IDs
                .filter(Objects::nonNull) // Removes null IDs
                .max(Comparator.naturalOrder()) // Finds highest ID
                .orElse(0L) + 1; // If none exists, starts from 1
    }

    // Writes all users to file
    private void writeAll(List<User> users) {

        // Converts each user into file line format and writes them
        fileHandler.writeLines(filePath, users.stream().map(this::toLine).toList());
    }

    // Converts User object into file line
    private String toLine(User u) {

        return u.getId() + DELIM + esc(u.getName()) + DELIM + esc(u.getEmail()) + DELIM
                + esc(u.getPhone()) + DELIM + esc(u.getAddress()) + DELIM
                + esc(u.getRole()) + DELIM + esc(u.getPasswordHash()) + DELIM
                + (u.getCreatedAt() == null ? "" : FMT.format(u.getCreatedAt())) + DELIM
                + (u.getUpdatedAt() == null ? "" : FMT.format(u.getUpdatedAt()));

        // Fields are separated using "|" delimiter
    }

    // Converts a file line back into a User object
    private User parseLine(String line) {

        // Splits line using "|" delimiter
        String[] p = line.split("\\|", -1);

        // Checks if line contains all required fields
        if (p.length < 9)
            throw new IllegalStateException("Corrupted user row: " + line);

        // Creates and returns User object
        return new User(
                Long.parseLong(p[0]), // User ID
                unesc(p[1]), // Name
                unesc(p[2]), // Email
                unesc(p[3]), // Phone
                unesc(p[4]), // Address
                unesc(p[5]), // Role
                unesc(p[6]), // Password hash
                parsedt(p[7]), // Created date
                parsedt(p[8]) // Updated date
        );
    }

    // Converts String date into LocalDateTime
    private LocalDateTime parsedt(String v) {

        // Returns null if value is empty
        return (v == null || v.isBlank()) ? null : LocalDateTime.parse(v, FMT);
    }

    // Escapes special characters before saving
    private String esc(String v) {

        // Returns empty string if null
        if (v == null)
            return "";

        // Escapes backslashes, delimiters, and new lines
        return v.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n");
    }

    // Restores escaped characters back to original form
    private String unesc(String v) {

        // Returns empty string if null or blank
        if (v == null || v.isBlank())
            return "";

        StringBuilder sb = new StringBuilder(); // Stores final result
        boolean esc = false; // Tracks escape sequence

        // Loops through each character
        for (char c : v.toCharArray()) {

            if (esc) { // If previous character was backslash

                sb.append(c == 'n' ? '\n' : c); // Converts "\n" into new line
                esc = false;

            } else if (c == '\\') {

                esc = true; // Marks next character as escaped

            } else {

                sb.append(c); // Adds normal character
            }
        }

        return sb.toString(); // Returns unescaped string
    }
}