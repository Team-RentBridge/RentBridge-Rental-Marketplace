package com.rentbridge.service;

import com.rentbridge.dto.AddReviewRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getEquipmentReviews(Long equipmentId) {
        // reviews uses user_id (not reviewer_id); join with users for name
        List<Map<String, Object>> reviews = jdbcTemplate.queryForList(
            "SELECT r.*, u.name AS reviewer_name " +
            "FROM reviews r " +
            "JOIN users u ON r.user_id = u.id " +
            "WHERE r.equipment_id = ? " +
            "ORDER BY r.created_at DESC",
            equipmentId);

        Map<String, Object> stats;
        try {
            stats = jdbcTemplate.queryForMap(
                "SELECT COALESCE(AVG(rating), 0) AS avg_rating, COUNT(*) AS total_reviews " +
                "FROM reviews WHERE equipment_id = ?",
                equipmentId);
        } catch (EmptyResultDataAccessException e) {
            stats = new HashMap<>();
            stats.put("avg_rating", 0.0);
            stats.put("total_reviews", 0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reviews", reviews);
        result.put("avg_rating", stats.get("avg_rating"));
        result.put("total_reviews", stats.get("total_reviews"));
        return result;
    }

    public Map<String, Object> getOwnerReviews(Long ownerId) {
        Map<String, Object> stats;
        try {
            stats = jdbcTemplate.queryForMap(
                "SELECT COALESCE(AVG(r.rating), 0) AS avg_rating, COUNT(r.id) AS total_reviews " +
                "FROM reviews r " +
                "JOIN equipment e ON r.equipment_id = e.id " +
                "WHERE e.owner_id = ?",
                ownerId);
        } catch (EmptyResultDataAccessException e) {
            stats = new HashMap<>();
            stats.put("avg_rating", 0.0);
            stats.put("total_reviews", 0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("avg_rating", stats.get("avg_rating"));
        result.put("total_reviews", stats.get("total_reviews"));
        return result;
    }

    public Map<String, Object> addReview(AddReviewRequest request, Long reviewerId) {
        Map<String, Object> equipment;
        try {
            equipment = jdbcTemplate.queryForMap(
                "SELECT owner_id FROM equipment WHERE id = ?", request.getEquipmentId());
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }

        Long ownerId = ((Number) equipment.get("owner_id")).longValue();

        // reviews uses user_id (not reviewer_id)
        return jdbcTemplate.queryForMap(
            "INSERT INTO reviews (equipment_id, user_id, owner_id, rating, comment) " +
            "VALUES (?, ?, ?, ?, ?) RETURNING *",
            request.getEquipmentId(),
            reviewerId,
            ownerId,
            request.getRating(),
            request.getComment()
        );
    }

    public void deleteReview(Long reviewId, Long userId, boolean isAdmin) {
        int rows;
        if (isAdmin) {
            rows = jdbcTemplate.update("DELETE FROM reviews WHERE id = ?", reviewId);
        } else {
            rows = jdbcTemplate.update(
                "DELETE FROM reviews WHERE id = ? AND user_id = ?", reviewId, userId);
        }
        if (rows == 0) {
            throw new RuntimeException("Review not found or not authorized");
        }
    }
}
