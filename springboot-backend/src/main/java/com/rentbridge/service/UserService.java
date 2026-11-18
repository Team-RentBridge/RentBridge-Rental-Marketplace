package com.rentbridge.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                "SELECT id, name, email, role FROM users WHERE id = ?", userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new RuntimeException("User not found");
        }

        Integer totalListings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM equipment WHERE owner_id = ?", Integer.class, userId);

        Integer totalRentalsGiven = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings b JOIN equipment e ON b.equipment_id = e.id WHERE e.owner_id = ?",
            Integer.class, userId);

        // bookings uses user_id
        Integer totalRentalsTaken = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE user_id = ?", Integer.class, userId);

        Object totalRevenueObj = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(b.total_price), 0) FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id WHERE e.owner_id = ? AND b.status = 'completed'",
            Object.class, userId);
        double totalRevenue = totalRevenueObj != null ? ((Number) totalRevenueObj).doubleValue() : 0.0;

        Integer activeRentals = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE user_id = ? AND status = 'active'",
            Integer.class, userId);

        // penalties table: booking_id, late_days, penalty_amount (no renter_id)
        // Calculate via bookings join
        Object pendingDuesObj = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(p.penalty_amount), 0) FROM penalties p " +
            "JOIN bookings b ON p.booking_id = b.id WHERE b.user_id = ?",
            Object.class, userId);
        double pendingDues = pendingDuesObj != null ? ((Number) pendingDuesObj).doubleValue() : 0.0;

        int completedRentals = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE user_id = ? AND status = 'completed'",
            Integer.class, userId);
        int totalUserRentals = totalRentalsTaken != null ? totalRentalsTaken : 0;
        double credibilityScore = totalUserRentals > 0
            ? Math.min(5.0, ((double) completedRentals / totalUserRentals) * 5.0)
            : 5.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListings", totalListings != null ? totalListings : 0);
        stats.put("totalRentalsGiven", totalRentalsGiven != null ? totalRentalsGiven : 0);
        stats.put("totalRentalsTaken", totalRentalsTaken != null ? totalRentalsTaken : 0);
        stats.put("totalRevenue", totalRevenue);
        stats.put("activeRentals", activeRentals != null ? activeRentals : 0);
        stats.put("pendingDues", pendingDues);
        stats.put("credibilityScore", credibilityScore);

        Map<String, Object> result = new HashMap<>(user);
        result.put("stats", stats);
        return result;
    }

    public Map<String, Object> getUserTransactions(Long userId) {
        List<Map<String, Object>> borrowed = jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS equipment_title, e.image_url, e.price_per_day, " +
            "u.name AS owner_name " +
            "FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id " +
            "JOIN users u ON e.owner_id = u.id " +
            "WHERE b.user_id = ? ORDER BY b.id DESC",
            userId);

        List<Map<String, Object>> lent = jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS equipment_title, e.image_url, e.price_per_day, " +
            "u.name AS renter_name " +
            "FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id " +
            "JOIN users u ON b.user_id = u.id " +
            "WHERE e.owner_id = ? ORDER BY b.id DESC",
            userId);

        Map<String, Object> result = new HashMap<>();
        result.put("borrowed", borrowed);
        result.put("lent", lent);
        return result;
    }
}
