package com.rentbridge.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getTrustProfile(Long userId) {
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                "SELECT id, name, role, created_at, identity_verified, business_verified, document_verified " +
                "FROM users WHERE id = ?", userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new RuntimeException("User not found");
        }

        Map<String, Object> rentalStats = jdbcTemplate.queryForMap(
            "SELECT " +
            "COUNT(*) FILTER (WHERE b.status = 'completed' AND COALESCE(e.is_for_sale, FALSE) = FALSE) AS completed, " +
            "COUNT(*) FILTER (WHERE b.status IN ('cancelled', 'declined', 'expired') " +
            "AND COALESCE(e.is_for_sale, FALSE) = FALSE) AS cancellations " +
            "FROM bookings b JOIN equipment e ON e.id = b.equipment_id WHERE e.owner_id = ?", userId);

        int completed = ((Number) rentalStats.get("completed")).intValue();
        int cancellations = ((Number) rentalStats.get("cancellations")).intValue();
        int resolvedRentals = completed + cancellations;
        double completionRate = resolvedRentals > 0 ? (double) completed / resolvedRentals : 0.0;
        double completionPoints = completionRate * 40.0;

        Map<String, Object> reviewStats = jdbcTemplate.queryForMap(
            "SELECT COALESCE(AVG(r.rating), 0) AS avg_rating, COUNT(r.id) AS total_reviews " +
            "FROM reviews r JOIN equipment e ON r.equipment_id = e.id WHERE e.owner_id = ?", userId);
        double avgRating = ((Number) reviewStats.get("avg_rating")).doubleValue();
        int reviewCount = ((Number) reviewStats.get("total_reviews")).intValue();
        double reviewPoints = (avgRating / 5.0) * 15.0
            + Math.min(reviewCount / 10.0, 1.0) * 5.0;

        boolean identityVerified = Boolean.TRUE.equals(user.get("identity_verified"));
        boolean businessVerified = Boolean.TRUE.equals(user.get("business_verified"));
        boolean documentVerified = Boolean.TRUE.equals(user.get("document_verified"));
        double verificationPoints = (identityVerified ? 15.0 : 0.0)
            + (businessVerified ? 10.0 : 0.0)
            + (documentVerified ? 5.0 : 0.0);

        Map<String, Object> responseStats = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) AS response_count, " +
            "COALESCE(AVG(EXTRACT(EPOCH FROM (b.responded_at - b.created_at)) / 3600.0), 0) AS avg_hours " +
            "FROM bookings b JOIN equipment e ON e.id = b.equipment_id " +
            "WHERE e.owner_id = ? AND b.responded_at IS NOT NULL AND b.created_at IS NOT NULL", userId);
        int responseCount = ((Number) responseStats.get("response_count")).intValue();
        double avgResponseHours = ((Number) responseStats.get("avg_hours")).doubleValue();
        double responsePoints = 0.0;
        if (responseCount > 0) {
            if (avgResponseHours <= 2) responsePoints = 10.0;
            else if (avgResponseHours <= 8) responsePoints = 8.0;
            else if (avgResponseHours <= 24) responsePoints = 6.0;
            else if (avgResponseHours <= 48) responsePoints = 3.0;
        }

        long score = Math.round(completionPoints + reviewPoints + verificationPoints + responsePoints);
        score = Math.max(0, Math.min(100, score));
        String label = resolvedRentals >= 3 || reviewCount >= 3
            ? (score >= 80 ? "Strong" : score >= 60 ? "Established" : score >= 40 ? "Developing" : "Limited evidence")
            : "New or limited activity";

        Map<String, Object> indicators = new LinkedHashMap<>();
        indicators.put("completion", Map.of(
            "label", "Completed rentals",
            "value", resolvedRentals > 0 ? Math.round(completionRate * 100.0) + "%" : "No resolved rentals",
            "detail", completed + " completed, " + cancellations + " cancelled/declined/expired",
            "points", Math.round(completionPoints),
            "maxPoints", 40));
        indicators.put("reviews", Map.of(
            "label", "Review quality",
            "value", reviewCount > 0 ? String.format("%.1f", avgRating) + "/5" : "No reviews",
            "detail", reviewCount + " verified rental " + (reviewCount == 1 ? "review" : "reviews"),
            "points", Math.round(reviewPoints),
            "maxPoints", 20));
        indicators.put("verification", Map.of(
            "label", "Document verification",
            "value", (identityVerified ? "Identity " : "") + (businessVerified ? "Business " : "") + (documentVerified ? "Documents" : ""),
            "detail", "Only statuses set by platform review are shown",
            "points", Math.round(verificationPoints),
            "maxPoints", 30));
        indicators.put("response", Map.of(
            "label", "Owner response time",
            "value", responseCount > 0 ? String.format("%.1f", avgResponseHours) + " hr average" : "Not enough data",
            "detail", responseCount + " approval/decline responses tracked",
            "points", Math.round(responsePoints),
            "maxPoints", 10));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("owner", user);
        result.put("score", score);
        result.put("label", label);
        result.put("completedRentals", completed);
        result.put("cancelledRentals", cancellations);
        result.put("averageRating", avgRating);
        result.put("reviewCount", reviewCount);
        result.put("identityVerified", identityVerified);
        result.put("businessVerified", businessVerified);
        result.put("documentVerified", documentVerified);
        result.put("responseCount", responseCount);
        result.put("averageResponseHours", responseCount > 0 ? avgResponseHours : null);
        result.put("indicators", indicators);
        result.put("methodology", "Rule-based: 40 points for completion rate, 20 for review quality and volume, 30 for platform verification, and 10 for tracked owner response time. No unverified reports or fraud labels are used.");
        return result;
    }

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
            "SELECT COUNT(*) FROM bookings WHERE user_id = ? " +
            "AND status IN ('active', 'confirmed', 'pending_approval')",
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
        Map<String, Object> trustProfile = getTrustProfile(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListings", totalListings != null ? totalListings : 0);
        stats.put("totalRentalsGiven", totalRentalsGiven != null ? totalRentalsGiven : 0);
        stats.put("totalRentalsTaken", totalRentalsTaken != null ? totalRentalsTaken : 0);
        stats.put("totalRevenue", totalRevenue);
        stats.put("activeRentals", activeRentals != null ? activeRentals : 0);
        stats.put("pendingDues", pendingDues);
        stats.put("credibilityScore", ((Number) trustProfile.get("score")).doubleValue() / 20.0);
        stats.put("reliabilityScore", trustProfile.get("score"));
        stats.put("reliabilityLabel", trustProfile.get("label"));
        stats.put("trustProfile", trustProfile);

        Map<String, Object> result = new HashMap<>(user);
        result.put("stats", stats);
        return result;
    }

    public Map<String, Object> getUserTransactions(Long userId) {
        List<Map<String, Object>> borrowed = jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS title, e.category, e.title AS equipment_title, " +
            "e.image_url, e.price_per_day, e.is_for_sale, FALSE AS is_owner, " +
            "u.name AS owner_name " +
            "FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id " +
            "JOIN users u ON e.owner_id = u.id " +
            "WHERE b.user_id = ? ORDER BY b.id DESC",
            userId);

        List<Map<String, Object>> lent = jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS title, e.category, e.title AS equipment_title, " +
            "e.image_url, e.price_per_day, e.is_for_sale, TRUE AS is_owner, " +
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
