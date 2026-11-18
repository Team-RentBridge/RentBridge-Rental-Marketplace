package com.rentbridge.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getAdminStats() {
        Integer totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer totalEquipment = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class);
        Integer totalBookings = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
        Integer activeBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE status IN ('active', 'confirmed', 'pending_approval')",
            Integer.class);

        Object totalRevenueObj = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_price), 0) FROM bookings WHERE status = 'completed'", Object.class);
        double totalRevenue = totalRevenueObj != null ? ((Number) totalRevenueObj).doubleValue() : 0.0;

        Integer totalPenalties = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM penalties", Integer.class);
        Object pendingPenaltiesObj = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(penalty_amount), 0) FROM penalties", Object.class);
        double pendingPenalties = pendingPenaltiesObj != null ? ((Number) pendingPenaltiesObj).doubleValue() : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers != null ? totalUsers : 0);
        stats.put("totalEquipment", totalEquipment != null ? totalEquipment : 0);
        stats.put("totalBookings", totalBookings != null ? totalBookings : 0);
        stats.put("activeBookings", activeBookings != null ? activeBookings : 0);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalPenalties", totalPenalties != null ? totalPenalties : 0);
        stats.put("pendingPenalties", pendingPenalties);
        return stats;
    }

    public List<Map<String, Object>> getAllUsers() {
        return jdbcTemplate.queryForList(
            "SELECT id, name, email, role FROM users ORDER BY id DESC");
    }

    public List<Map<String, Object>> getAllBookings() {
        // bookings uses user_id; join with users for renter_name
        return jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS equipment_title, u.name AS renter_name, u.email AS renter_email " +
            "FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id " +
            "JOIN users u ON b.user_id = u.id " +
            "ORDER BY b.id DESC");
    }

    public void deleteUser(Long userId) {
        int rows = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        if (rows == 0) {
            throw new RuntimeException("User not found");
        }
    }
}
