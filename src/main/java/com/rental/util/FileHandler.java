package com.rental.util;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

// Util: Handles all file read/write operations for data storage
@Component
public class FileHandler {

    public void ensureFileExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create file: " + path, e);
        }
    }

    public synchronized List<String> readLines(Path path) {
        try {
            if (!Files.exists(path)) return new ArrayList<>();
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                if (line != null && !line.isBlank()) lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new RuntimeException("Could not read file: " + path, e);
        }
    }

    public synchronized void writeLines(Path path, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    sb.append(line).append(System.lineSeparator());
                }
            }
            Files.writeString(path, sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not write file: " + path, e);
        }
    }
}
