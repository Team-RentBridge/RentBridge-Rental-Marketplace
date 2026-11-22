package com.rentbridge.service;

import com.rentbridge.dto.AddEquipmentRequest;
import com.rentbridge.dto.CreateEquipmentBlockRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
            "u.name AS owner_name, " +
            "COALESCE(AVG(r.rating), 0) AS avg_rating, " +
            "COUNT(r.id) AS total_reviews " +
            "FROM equipment e " +
            "LEFT JOIN users u ON e.owner_id = u.id " +
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

        sql.append("GROUP BY e.id, u.name ORDER BY e.id DESC");

        if (featured != null && featured.equalsIgnoreCase("true")) {
            sql.append(" LIMIT 6");
        }

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getEquipmentById(Long id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT e.*, " +
                "u.name AS owner_name, " +
                "COALESCE(AVG(r.rating), 0) AS avg_rating, " +
                "COUNT(r.id) AS total_reviews " +
                "FROM equipment e " +
                "LEFT JOIN users u ON e.owner_id = u.id " +
                "LEFT JOIN reviews r ON e.id = r.equipment_id " +
                "WHERE e.id = ? " +
                "GROUP BY e.id, u.name",
                id);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }
    }

    public List<Map<String, Object>> getAvailability(Long equipmentId, LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw new RuntimeException("Choose a date range of 1 to 366 days");
        }
        try {
            jdbcTemplate.queryForObject(
                "SELECT id FROM equipment WHERE id = ?", Long.class, equipmentId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }

        return jdbcTemplate.queryForList(
            "SELECT dates.day::date AS date, " +
            "CASE WHEN (e.available_from IS NOT NULL AND dates.day::date < e.available_from) " +
            "OR (e.available_to IS NOT NULL AND e.available_to >= ?::date " +
            "AND dates.day::date > e.available_to) THEN 0 " +
            "ELSE GREATEST(e.total_quantity - COALESCE((" +
            "  SELECT SUM(b.quantity) FROM bookings b " +
            "  WHERE b.equipment_id = e.id " +
            "    AND b.start_date <= dates.day::date AND b.end_date > dates.day::date " +
            "    AND (b.status IN ('pending', 'confirmed', 'active') " +
            "      OR (b.status = 'payment_pending' AND b.hold_expires_at > NOW()) " +
            "      OR (b.status = 'pending_approval' AND b.approval_expires_at > NOW()))" +
            "), 0), 0) END AS available_quantity, " +
            "EXISTS (SELECT 1 FROM equipment_blocks eb " +
            "  WHERE eb.equipment_id = e.id " +
            "    AND eb.start_date <= dates.day::date AND eb.end_date > dates.day::date) AS owner_blocked, " +
            "(SELECT eb.id FROM equipment_blocks eb " +
            "  WHERE eb.equipment_id = e.id " +
            "    AND eb.start_date <= dates.day::date AND eb.end_date > dates.day::date LIMIT 1) AS owner_block_id " +
            "FROM equipment e " +
            "CROSS JOIN LATERAL generate_series(?::date, (?::date - 1), INTERVAL '1 day') AS dates(day) " +
            "WHERE e.id = ? ORDER BY dates.day",
            LocalDate.now(), from, to, equipmentId);
    }

    @Transactional
    public Map<String, Object> addAvailabilityBlock(
            Long equipmentId, CreateEquipmentBlockRequest request, Long userId) {
        LocalDate start = parseDate(request.getStartDate(), "Start date");
        LocalDate end = parseDate(request.getEndDate(), "End date");
        if (!end.isAfter(start)) {
            throw new RuntimeException("End date must be after start date");
        }

        Map<String, Object> equipment;
        try {
            equipment = jdbcTemplate.queryForMap(
                "SELECT id, owner_id FROM equipment WHERE id = ? FOR UPDATE", equipmentId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }
        if (equipment.get("owner_id") == null
                || !userId.equals(((Number) equipment.get("owner_id")).longValue())) {
            throw new RuntimeException("Only the listing owner can block dates");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new RuntimeException("Blocked dates must start today or later");
        }

        Integer conflictingBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings b WHERE b.equipment_id = ? " +
            "AND b.start_date < ?::date AND b.end_date > ?::date " +
            "AND (b.status IN ('pending', 'confirmed', 'active') " +
            "OR (b.status = 'payment_pending' AND b.hold_expires_at > NOW()) " +
            "OR (b.status = 'pending_approval' AND b.approval_expires_at > NOW()))",
            Integer.class, equipmentId, end.toString(), start.toString());
        if (conflictingBookings != null && conflictingBookings > 0) {
            throw new RuntimeException("These dates already have a reservation");
        }
        Integer overlappingBlocks = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM equipment_blocks " +
            "WHERE equipment_id = ? AND start_date < ?::date AND end_date > ?::date",
            Integer.class, equipmentId, end.toString(), start.toString());
        if (overlappingBlocks != null && overlappingBlocks > 0) {
            throw new RuntimeException("These dates are already blocked");
        }

        return jdbcTemplate.queryForMap(
            "INSERT INTO equipment_blocks (equipment_id, created_by, start_date, end_date, reason) " +
            "VALUES (?, ?, ?::date, ?::date, ?) RETURNING *",
            equipmentId, userId, start.toString(), end.toString(), request.getReason());
    }

    @Transactional
    public void removeAvailabilityBlock(Long equipmentId, Long blockId, Long userId) {
        int deleted = jdbcTemplate.update(
            "DELETE FROM equipment_blocks eb USING equipment e " +
            "WHERE eb.id = ? AND eb.equipment_id = ? AND e.id = eb.equipment_id AND e.owner_id = ?",
            blockId, equipmentId, userId);
        if (deleted == 0) {
            throw new RuntimeException("Blocked dates not found or not authorized");
        }
    }

    private LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(label + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new RuntimeException(label + " must use YYYY-MM-DD format");
        }
    }

    public Map<String, Object> addEquipment(AddEquipmentRequest request, Long ownerId) {
        boolean isForSale = request.getIsForSale() != null && request.getIsForSale();
        boolean deliveryAvailable = !isForSale
            && request.getDeliveryAvailable() != null && request.getDeliveryAvailable();
        if (deliveryAvailable && (request.getDeliveryAreas() == null
                || request.getDeliveryAreas().isBlank())) {
            throw new RuntimeException("Add at least one delivery service area");
        }
        if (request.getDeliveryFee() != null && request.getDeliveryFee().signum() < 0) {
            throw new RuntimeException("Delivery fee cannot be negative");
        }
        if (!isForSale && request.getPickupWindows() != null
                && request.getPickupWindows().isBlank()) {
            throw new RuntimeException("Add at least one pickup window");
        }
        return jdbcTemplate.queryForMap(
            "INSERT INTO equipment (title, description, price_per_day, penalty_per_day, category, " +
            "available_from, available_to, image_url, quantity, total_quantity, instant_booking, " +
            "is_featured, is_for_sale, buy_price, owner_id, delivery_available, delivery_areas, " +
            "delivery_fee, pickup_windows) " +
            "VALUES (?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
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
            request.getQuantity() != null ? request.getQuantity() : 1,
            request.getInstantBooking() != null && request.getInstantBooking(),
            request.getIsFeatured() != null ? request.getIsFeatured() : false,
            request.getIsForSale() != null ? request.getIsForSale() : false,
            request.getBuyPrice(),
            ownerId,
            deliveryAvailable,
            request.getDeliveryAreas(),
            request.getDeliveryFee() != null ? request.getDeliveryFee() : java.math.BigDecimal.ZERO,
            request.getPickupWindows() != null && !request.getPickupWindows().isBlank()
                ? request.getPickupWindows() : "9:00 AM - 12:00 PM\n1:00 PM - 5:00 PM"
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
