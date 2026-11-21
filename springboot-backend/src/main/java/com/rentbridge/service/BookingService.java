package com.rentbridge.service;

import com.rentbridge.dto.CreateBookingRequest;
import com.rentbridge.dto.ReturnBookingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private static final String RESERVING_STATUSES =
        "('pending', 'confirmed', 'active', 'payment_pending', 'pending_approval')";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> createRentalHold(CreateBookingRequest request, Long userId) {
        LocalDate startDate = parseDate(request.getStartDate(), "Start date");
        LocalDate endDate = parseDate(request.getEndDate(), "End date");
        if (!endDate.isAfter(startDate)) {
            throw new RuntimeException("Return date must be after pickup date");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        if (quantity < 1) {
            throw new RuntimeException("Quantity must be at least one");
        }

        Map<String, Object> equipment = lockEquipment(request.getEquipmentId());
        if (Boolean.TRUE.equals(equipment.get("is_for_sale"))) {
            throw new RuntimeException("This listing is for sale, not rental");
        }
        if (equipment.get("owner_id") != null
                && userId.equals(((Number) equipment.get("owner_id")).longValue())) {
            throw new RuntimeException("You cannot book your own listing");
        }

        validateListingDates(equipment, startDate, endDate);

        Integer reservedQuantity = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM bookings " +
            "WHERE equipment_id = ? AND start_date < ?::date AND end_date > ?::date " +
            "AND (status IN " + RESERVING_STATUSES + ") " +
            "AND (status <> 'payment_pending' OR hold_expires_at > NOW()) " +
            "AND (status <> 'pending_approval' OR approval_expires_at > NOW())",
            Integer.class, request.getEquipmentId(), endDate.toString(), startDate.toString());

        int totalQuantity = ((Number) equipment.get("total_quantity")).intValue();
        if (totalQuantity - (reservedQuantity == null ? 0 : reservedQuantity) < quantity) {
            throw new RuntimeException("Those dates no longer have enough equipment available");
        }

        BigDecimal pricePerDay = decimalValue(equipment.get("price_per_day"), "Daily rental price");
        BigDecimal totalPrice = pricePerDay
            .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate)))
            .multiply(BigDecimal.valueOf(quantity));

        return jdbcTemplate.queryForMap(
            "INSERT INTO bookings (" +
            "equipment_id, user_id, start_date, end_date, total_price, quantity, status, " +
            "hold_expires_at, pickup_at, return_by, cancellation_deadline" +
            ") VALUES (?, ?, ?::date, ?::date, ?, ?, 'payment_pending', " +
            "NOW() + INTERVAL '15 minutes', ?::date + TIME '09:00', " +
            "?::date + TIME '17:00', ?::date - INTERVAL '1 day' + TIME '09:00') " +
            "RETURNING *",
            request.getEquipmentId(), userId, startDate.toString(), endDate.toString(),
            totalPrice, quantity, startDate.toString(), endDate.toString(), startDate.toString());
    }

    @Transactional
    public Map<String, Object> paymentSucceeded(
            Long bookingId, Long userId, String paymentId, String orderId) {
        Map<String, Object> booking;
        try {
            booking = jdbcTemplate.queryForMap(
                "SELECT b.*, e.owner_id FROM bookings b " +
                "JOIN equipment e ON e.id = b.equipment_id " +
                "WHERE b.id = ? AND b.user_id = ? FOR UPDATE OF e, b",
                bookingId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Booking hold not found");
        }

        String currentStatus = (String) booking.get("status");
        if (paymentId.equals(booking.get("payment_id")) && orderId.equals(booking.get("payment_order_id"))) {
            return booking;
        }
        if ("expired".equals(currentStatus) || "cancelled".equals(currentStatus)
                || ("payment_pending".equals(currentStatus) && booking.get("hold_expires_at") != null
                    && !isHoldActive(bookingId))) {
            return jdbcTemplate.queryForMap(
                "UPDATE bookings SET status = CASE WHEN status = 'payment_pending' THEN 'expired' ELSE status END, " +
                "hold_expires_at = NULL, " +
                "payment_id = ?, payment_order_id = ?, refund_status = 'pending', refund_updated_at = NOW() " +
                "WHERE id = ? AND user_id = ? AND status IN ('expired', 'cancelled', 'payment_pending') RETURNING *",
                paymentId, orderId, bookingId, userId);
        }
        if (!"payment_pending".equals(currentStatus)) {
            throw new RuntimeException("This booking hold has already expired or been completed");
        }
        if (booking.get("hold_expires_at") == null) {
            throw new RuntimeException("This booking hold has expired");
        }
        try {
            return jdbcTemplate.queryForMap(
                "UPDATE bookings b SET " +
                "status = CASE WHEN e.instant_booking THEN 'confirmed' ELSE 'pending_approval' END, " +
                "hold_expires_at = NULL, " +
                "approval_expires_at = CASE WHEN e.instant_booking THEN NULL ELSE NOW() + INTERVAL '24 hours' END, " +
                "payment_id = ?, payment_order_id = ?, refund_status = NULL " +
                "FROM equipment e " +
                "WHERE b.id = ? AND b.user_id = ? AND e.id = b.equipment_id " +
                "AND b.status = 'payment_pending' AND b.hold_expires_at > NOW() " +
                "RETURNING b.*",
                paymentId, orderId, bookingId, userId);
        } catch (EmptyResultDataAccessException e) {
            return jdbcTemplate.queryForMap(
                "UPDATE bookings SET " +
                "status = CASE WHEN status = 'payment_pending' THEN 'expired' ELSE status END, " +
                "hold_expires_at = NULL, payment_id = ?, payment_order_id = ?, " +
                "refund_status = 'pending', refund_updated_at = NOW() " +
                "WHERE id = ? AND user_id = ? AND status IN ('payment_pending', 'cancelled') RETURNING *",
                paymentId, orderId, bookingId, userId);
        }
    }

    @Transactional
    public Map<String, Object> approveBooking(Long bookingId, Long ownerId) {
        Map<String, Object> booking = getBookingForOwnerUpdate(bookingId, ownerId);
        if (!"pending_approval".equals(booking.get("status"))) {
            throw new RuntimeException("This booking is not waiting for approval");
        }
        Integer approvalActive = jdbcTemplate.queryForObject(
            "SELECT CASE WHEN approval_expires_at > NOW() THEN 1 ELSE 0 END " +
            "FROM bookings WHERE id = ?", Integer.class, bookingId);
        if (approvalActive == null || approvalActive != 1) {
            throw new RuntimeException("The 24-hour approval window has expired");
        }
        return jdbcTemplate.queryForMap(
            "UPDATE bookings SET status = 'confirmed', approval_expires_at = NULL, responded_at = NOW() " +
            "WHERE id = ? AND status = 'pending_approval' AND approval_expires_at > NOW() RETURNING *",
            bookingId);
    }

    @Transactional
    public Map<String, Object> declineBooking(Long bookingId, Long ownerId) {
        Map<String, Object> booking = getBookingForOwnerUpdate(bookingId, ownerId);
        if (!"pending_approval".equals(booking.get("status"))) {
            throw new RuntimeException("This booking is not waiting for approval");
        }
        return jdbcTemplate.queryForMap(
            "UPDATE bookings SET status = 'declined', approval_expires_at = NULL, responded_at = NOW(), " +
            "refund_status = CASE WHEN payment_id IS NULL THEN NULL ELSE 'pending' END " +
            "WHERE id = ? AND status = 'pending_approval' RETURNING *",
            bookingId);
    }

    @Transactional
    public Map<String, Object> cancelBooking(Long bookingId, Long userId) {
        Map<String, Object> booking;
        try {
            booking = jdbcTemplate.queryForMap(
                "SELECT * FROM bookings WHERE id = ? AND user_id = ? FOR UPDATE",
                bookingId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Booking not found or not authorized");
        }

        String status = (String) booking.get("status");
        if (!"payment_pending".equals(status) && !"pending_approval".equals(status)
                && !"confirmed".equals(status)) {
            throw new RuntimeException("This booking cannot be cancelled");
        }
        if (!"payment_pending".equals(status) && booking.get("cancellation_deadline") != null) {
            Integer deadlinePassed = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN cancellation_deadline > NOW() THEN 0 ELSE 1 END " +
                "FROM bookings WHERE id = ?", Integer.class, bookingId);
            if (deadlinePassed != null && deadlinePassed == 1) {
                throw new RuntimeException("The free-cancellation deadline has passed");
            }
        }

        return jdbcTemplate.queryForMap(
            "UPDATE bookings SET status = 'cancelled', hold_expires_at = NULL, " +
            "approval_expires_at = NULL, " +
            "refund_status = CASE WHEN payment_id IS NULL THEN NULL ELSE 'pending' END " +
            "WHERE id = ? RETURNING *",
            bookingId);
    }

    @Transactional
    public void expireBookings() {
        jdbcTemplate.update(
            "UPDATE bookings SET status = 'expired', hold_expires_at = NULL " +
            "WHERE status = 'payment_pending' AND hold_expires_at <= NOW()");
        jdbcTemplate.update(
            "UPDATE bookings SET status = 'declined', approval_expires_at = NULL, " +
            "refund_status = CASE WHEN payment_id IS NULL THEN NULL ELSE 'pending' END " +
            "WHERE status = 'pending_approval' AND approval_expires_at <= NOW()");
    }

    private boolean isHoldActive(Long bookingId) {
        Integer active = jdbcTemplate.queryForObject(
            "SELECT CASE WHEN hold_expires_at > NOW() THEN 1 ELSE 0 END " +
            "FROM bookings WHERE id = ?", Integer.class, bookingId);
        return active != null && active == 1;
    }

    @Transactional
    public List<Map<String, Object>> claimPendingRefunds() {
        return jdbcTemplate.queryForList(
            "WITH to_refund AS (" +
            "  SELECT id FROM bookings WHERE payment_id IS NOT NULL " +
            "  AND status IN ('declined', 'cancelled', 'expired') " +
            "  AND (refund_status = 'pending' " +
            "    OR (refund_status = 'failed' AND refund_updated_at < NOW() - INTERVAL '5 minutes') " +
            "    OR (refund_status = 'processing' AND refund_updated_at < NOW() - INTERVAL '10 minutes')) " +
            "  ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED" +
            ") UPDATE bookings b SET refund_status = 'processing', refund_updated_at = NOW() " +
            "FROM to_refund WHERE b.id = to_refund.id " +
            "RETURNING b.id, b.payment_id"
        );
    }

    @Transactional
    public void setRefundStatus(Long bookingId, String status) {
        jdbcTemplate.update(
            "UPDATE bookings SET refund_status = ?, refund_updated_at = NOW() WHERE id = ?",
            status, bookingId);
    }

    @Transactional
    public Map<String, Object> createBooking(CreateBookingRequest request, Long userId) {
        Map<String, Object> equipment = lockEquipment(request.getEquipmentId());
        if (!Boolean.TRUE.equals(equipment.get("is_for_sale"))) {
            throw new RuntimeException("Rental listings must be booked from their availability calendar");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        int availableQuantity = ((Number) equipment.get("quantity")).intValue();
        if (quantity < 1 || availableQuantity < quantity) {
            throw new RuntimeException("Not enough items are available for purchase");
        }

        BigDecimal price = decimalValue(equipment.get("buy_price"), "Purchase price");
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));
        Map<String, Object> booking = jdbcTemplate.queryForMap(
            "INSERT INTO bookings (equipment_id, user_id, start_date, end_date, total_price, quantity, status) " +
            "VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + 1, ?, ?, 'completed') RETURNING *",
            request.getEquipmentId(), userId, totalPrice, quantity);
        jdbcTemplate.update(
            "UPDATE equipment SET quantity = quantity - ? WHERE id = ?",
            quantity, request.getEquipmentId());
        return booking;
    }

    public List<Map<String, Object>> getUserBookings(Long userId) {
        return jdbcTemplate.queryForList(
            "SELECT b.*, e.title AS equipment_title, e.image_url, e.price_per_day, e.is_for_sale, e.buy_price " +
            "FROM bookings b " +
            "JOIN equipment e ON b.equipment_id = e.id " +
            "WHERE b.user_id = ? " +
            "ORDER BY b.id DESC",
            userId);
    }

    @Transactional
    public Map<String, Object> returnBooking(ReturnBookingRequest request, Long userId) {
        Map<String, Object> booking;
        try {
            booking = jdbcTemplate.queryForMap(
                "SELECT b.*, e.penalty_per_day " +
                "FROM bookings b JOIN equipment e ON b.equipment_id = e.id " +
                "WHERE b.id = ? AND b.user_id = ? FOR UPDATE OF b",
                request.getBookingId(), userId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Booking not found or not authorized");
        }
        if (!"confirmed".equals(booking.get("status")) && !"active".equals(booking.get("status"))) {
            throw new RuntimeException("Only confirmed rentals can be returned");
        }

        Integer pickupStarted = jdbcTemplate.queryForObject(
            "SELECT CASE WHEN pickup_at <= NOW() THEN 1 ELSE 0 END " +
            "FROM bookings WHERE id = ?", Integer.class, request.getBookingId());
        if (pickupStarted == null || pickupStarted != 1) {
            throw new RuntimeException("This rental cannot be returned before its pickup time");
        }
        Long lateDays = jdbcTemplate.queryForObject(
            "SELECT GREATEST(CEIL(EXTRACT(EPOCH FROM (NOW() - return_by)) / 86400), 0)::bigint " +
            "FROM bookings WHERE id = ?", Long.class, request.getBookingId());
        if (lateDays == null) {
            lateDays = 0L;
        }
        int penaltyPerDay = booking.get("penalty_per_day") != null
            ? ((Number) booking.get("penalty_per_day")).intValue() : 0;
        int bookingQuantity = ((Number) booking.get("quantity")).intValue();
        int penaltyAmount = Math.multiplyExact(
            Math.multiplyExact(penaltyPerDay, Math.toIntExact(lateDays)), bookingQuantity);

        if (penaltyAmount > 0) {
            jdbcTemplate.update(
                "INSERT INTO penalties (booking_id, late_days, penalty_amount) VALUES (?, ?, ?)",
                request.getBookingId(), Math.toIntExact(lateDays), penaltyAmount);
        }

        jdbcTemplate.update(
            "UPDATE bookings SET status = 'completed' WHERE id = ?",
            request.getBookingId());
        Map<String, Object> result = jdbcTemplate.queryForMap(
            "SELECT * FROM bookings WHERE id = ?", request.getBookingId());
        result.put("lateDays", lateDays);
        result.put("penalty", penaltyAmount);
        return result;
    }

    private Map<String, Object> getBookingForOwnerUpdate(Long bookingId, Long ownerId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT b.* FROM bookings b JOIN equipment e ON e.id = b.equipment_id " +
                "WHERE b.id = ? AND e.owner_id = ? FOR UPDATE OF e, b",
                bookingId, ownerId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Booking not found or not authorized");
        }
    }

    private Map<String, Object> lockEquipment(Long equipmentId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT * FROM equipment WHERE id = ? FOR UPDATE", equipmentId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }
    }

    private void validateListingDates(Map<String, Object> equipment, LocalDate start, LocalDate end) {
        Object availableFrom = equipment.get("available_from");
        if (availableFrom != null && start.isBefore(LocalDate.parse(availableFrom.toString()))) {
            throw new RuntimeException("Pickup date is before this listing's availability window");
        }
        Object availableTo = equipment.get("available_to");
        if (availableTo != null
                && !LocalDate.parse(availableTo.toString()).isBefore(LocalDate.now())
                && end.minusDays(1).isAfter(LocalDate.parse(availableTo.toString()))) {
            throw new RuntimeException("Return date is after this listing's availability window");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new RuntimeException("Pickup date must be today or later");
        }
    }

    private BigDecimal decimalValue(Object value, String label) {
        if (!(value instanceof Number)) {
            throw new RuntimeException(label + " is not configured correctly");
        }
        return new BigDecimal(value.toString());
    }

    private LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(label + " is required");
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            throw new RuntimeException(label + " must use YYYY-MM-DD format");
        }
    }
}
