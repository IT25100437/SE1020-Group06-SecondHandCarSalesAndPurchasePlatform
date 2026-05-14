package com.rental.util;

import com.rental.model.Vehicle;
import java.util.ArrayList;
import java.util.List;

// DATA STRUCTURE: Custom Singly Linked List for Vehicle storage
// Used by VehicleService to store car listings dynamically
public class VehicleLinkedList {

    // Inner node class
    private static class Node {
        Vehicle vehicle;
        Node next;

        Node(Vehicle vehicle) {
            this.vehicle = vehicle;
            this.next = null;
        }
    }

    private Node head;
    private int size;

    public VehicleLinkedList() {
        this.head = null;
        this.size = 0;
    }

    // Add vehicle to the end of the list
    public void add(Vehicle vehicle) {
        Node newNode = new Node(vehicle);
        if (head == null) {
            head = newNode;
        } else {
            Node current = head;
            while (current.next != null) {
                current = current.next;
            }
            current.next = newNode;
        }
        size++;
    }

    // Add all from a Java List
    public void addAll(List<Vehicle> vehicles) {
        for (Vehicle v : vehicles) add(v);
    }

    // Remove vehicle by ID
    public boolean remove(Long id) {
        if (head == null) return false;
        if (head.vehicle.getId().equals(id)) {
            head = head.next;
            size--;
            return true;
        }
        Node current = head;
        while (current.next != null) {
            if (current.next.vehicle.getId().equals(id)) {
                current.next = current.next.next;
                size--;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    // Find vehicle by ID
    public Vehicle findById(Long id) {
        Node current = head;
        while (current != null) {
            if (current.vehicle.getId().equals(id)) return current.vehicle;
            current = current.next;
        }
        return null;
    }

    // Update a vehicle in the list
    public boolean update(Vehicle updated) {
        Node current = head;
        while (current != null) {
            if (current.vehicle.getId().equals(updated.getId())) {
                current.vehicle = updated;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    // Convert linked list to Java List
    public List<Vehicle> toList() {
        List<Vehicle> result = new ArrayList<>();
        Node current = head;
        while (current != null) {
            result.add(current.vehicle);
            current = current.next;
        }
        return result;
    }

    // Filter by status
    public List<Vehicle> filterByStatus(String status) {
        List<Vehicle> result = new ArrayList<>();
        Node current = head;
        while (current != null) {
            if (status.equalsIgnoreCase(current.vehicle.getStatus())) {
                result.add(current.vehicle);
            }
            current = current.next;
        }
        return result;
    }

    // Search by query (brand, model, description)
    public List<Vehicle> search(String query) {
        if (query == null || query.isBlank()) return toList();
        String q = query.trim().toLowerCase();
        List<Vehicle> result = new ArrayList<>();
        Node current = head;
        while (current != null) {
            Vehicle v = current.vehicle;
            if ((v.getBrand() != null && v.getBrand().toLowerCase().contains(q))
                    || (v.getModel() != null && v.getModel().toLowerCase().contains(q))
                    || (v.getDescription() != null && v.getDescription().toLowerCase().contains(q))
                    || (v.getType() != null && v.getType().toLowerCase().contains(q))) {
                result.add(v);
            }
            current = current.next;
        }
        return result;
    }

    public int size() { return size; }
    public boolean isEmpty() { return head == null; }
}
