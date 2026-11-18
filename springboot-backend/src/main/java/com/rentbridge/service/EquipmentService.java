package com.rentbridge.service;

import com.rentbridge.dto.AddEquipmentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EquipmentService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getAllEquipment(String price, String featured) {
        // equipment table has no created_at; use id for ordering
        StringBuilder sql = new StringBuilder(
            "SELECT e.*, " +
            "COALESCE(AVG(r.rating), 0) AS avg_rating, " +
            "COUNT(r.id) AS total_reviews " +
            "FROM equipment e " +
            "LEFT JOIN reviews r ON e.id = r.equipment_id "
        );

        List<Object> params = new ArrayList<>();
        boolean hasWhere = false;

        if (price != null && !price.isEmpty()) {
            sql.append("WHERE e.price_per_day <= ? ");
            params.add(Double.parseDouble(price));
            hasWhere = true;
        }

        if (featured != null && featured.equalsIgnoreCase("true")) {
            sql.append(hasWhere ? "AND " : "WHERE ");
            sql.append("e.is_featured = true ");
        }

        sql.append("GROUP BY e.id ORDER BY e.id DESC");

        if (featured != null && featured.equalsIgnoreCase("true")) {
            sql.append(" LIMIT 6");
        }

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getEquipmentById(Long id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT e.*, " +
                "COALESCE(AVG(r.rating), 0) AS avg_rating, " +
                "COUNT(r.id) AS total_reviews " +
                "FROM equipment e " +
                "LEFT JOIN reviews r ON e.id = r.equipment_id " +
                "WHERE e.id = ? " +
                "GROUP BY e.id",
                id);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }
    }

    public Map<String, Object> addEquipment(AddEquipmentRequest request, Long ownerId) {
        return jdbcTemplate.queryForMap(
            "INSERT INTO equipment (title, description, price_per_day, penalty_per_day, category, " +
            "available_from, available_to, image_url, quantity, is_featured, is_for_sale, buy_price, owner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?, ?, ?, ?) " +
            "RETURNING *",
            request.getTitle(),
            request.getDescription(),
            request.getPricePerDay(),
            request.getPenaltyPerDay(),
            request.getCategory(),
            request.getAvailableFrom(),
            request.getAvailableTo(),
            request.getImageUrl(),
            request.getQuantity() != null ? request.getQuantity() : 1,
            request.getIsFeatured() != null ? request.getIsFeatured() : false,
            request.getIsForSale() != null ? request.getIsForSale() : false,
            request.getBuyPrice(),
            ownerId
        );
    }

    public void deleteEquipment(Long id, Long userId) {
        int rows = jdbcTemplate.update(
            "DELETE FROM equipment WHERE id = ? AND owner_id = ?", id, userId);
        if (rows == 0) {
            throw new RuntimeException("Equipment not found or not authorized");
        }
    }
}
