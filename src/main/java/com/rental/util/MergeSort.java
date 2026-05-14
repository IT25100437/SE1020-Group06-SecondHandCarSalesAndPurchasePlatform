package com.rental.util;

import com.rental.model.Vehicle;
import java.util.List;
import java.util.ArrayList;

// ALGORITHM: Merge Sort for sorting car listings by price
// Used by VehicleService when returning sorted vehicle lists
public class MergeSort {

    // Sort a List of vehicles by price (ascending or descending)
    public static List<Vehicle> sortByPrice(List<Vehicle> vehicles, boolean ascending) {
        if (vehicles == null || vehicles.size() <= 1) return vehicles;
        List<Vehicle> copy = new ArrayList<>(vehicles);
        mergeSort(copy, 0, copy.size() - 1, ascending);
        return copy;
    }

    // Recursive merge sort
    private static void mergeSort(List<Vehicle> list, int left, int right, boolean ascending) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSort(list, left, mid, ascending);
        mergeSort(list, mid + 1, right, ascending);
        merge(list, left, mid, right, ascending);
    }

    // Merge two sorted halves
    private static void merge(List<Vehicle> list, int left, int mid, int right, boolean ascending) {
        List<Vehicle> leftPart = new ArrayList<>(list.subList(left, mid + 1));
        List<Vehicle> rightPart = new ArrayList<>(list.subList(mid + 1, right + 1));

        int i = 0, j = 0, k = left;

        while (i < leftPart.size() && j < rightPart.size()) {
            double leftPrice = leftPart.get(i).getPrice();
            double rightPrice = rightPart.get(j).getPrice();

            boolean takeLeft = ascending ? leftPrice <= rightPrice : leftPrice >= rightPrice;
            if (takeLeft) {
                list.set(k++, leftPart.get(i++));
            } else {
                list.set(k++, rightPart.get(j++));
            }
        }

        while (i < leftPart.size()) list.set(k++, leftPart.get(i++));
        while (j < rightPart.size()) list.set(k++, rightPart.get(j++));
    }

    // Sort by year (newest first)
    public static List<Vehicle> sortByYear(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.size() <= 1) return vehicles;
        List<Vehicle> copy = new ArrayList<>(vehicles);
        mergeSortByYear(copy, 0, copy.size() - 1);
        return copy;
    }

    private static void mergeSortByYear(List<Vehicle> list, int left, int right) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSortByYear(list, left, mid);
        mergeSortByYear(list, mid + 1, right);
        mergeByYear(list, left, mid, right);
    }

    private static void mergeByYear(List<Vehicle> list, int left, int mid, int right) {
        List<Vehicle> leftPart = new ArrayList<>(list.subList(left, mid + 1));
        List<Vehicle> rightPart = new ArrayList<>(list.subList(mid + 1, right + 1));
        int i = 0, j = 0, k = left;
        while (i < leftPart.size() && j < rightPart.size()) {
            // Newest first
            if (leftPart.get(i).getYear() >= rightPart.get(j).getYear()) {
                list.set(k++, leftPart.get(i++));
            } else {
                list.set(k++, rightPart.get(j++));
            }
        }
        while (i < leftPart.size()) list.set(k++, leftPart.get(i++));
        while (j < rightPart.size()) list.set(k++, rightPart.get(j++));
    }
}
