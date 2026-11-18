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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> createBooking(CreateBookingRequest request, Long userId) {
        Map<String, Object> equipment;
        try {
            equipment = jdbcTemplate.queryForMap(
                "SELECT * FROM equipment WHERE id = ?", request.getEquipmentId());
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Equipment not found");
        }

        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        int availableQty = ((Number) equipment.get("quantity")).intValue();

        if (availableQty < quantity) {
            throw new RuntimeException("Not enough quantity available");
        }

        // total_price is integer in DB
        int totalPrice;
        boolean isForSale = equipment.get("is_for_sale") != null && (Boolean) equipment.get("is_for_sale");

        if (isForSale) {
            Object buyPriceObj = equipment.get("buy_price");
            int buyPrice = buyPriceObj != null ? ((Number) buyPriceObj).intValue() : 0;
            totalPrice = buyPrice * quantity;
        } else {
            LocalDate startDate = LocalDate.parse(request.getStartDate());
            LocalDate endDate = LocalDate.parse(request.getEndDate());
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days <= 0) days = 1;
            int pricePerDay = ((Number) equipment.get("price_per_day")).intValue();
            totalPrice = (int)(pricePerDay * days * quantity);
        }

        // bookings uses user_id (not renter_id)
        Map<String, Object> booking = jdbcTemplate.queryForMap(
            "INSERT INTO bookings (equipment_id, user_id, start_date, end_date, total_price, quantity, status) " +
            "VALUES (?, ?, ?::date, ?::date, ?, ?, 'active') RETURNING *",
            request.getEquipmentId(),
            userId,
            request.getStartDate(),
            request.getEndDate(),
            totalPrice,
            quantity
        );

        jdbcTemplate.update(
            "UPDATE equipment SET quantity = quantity - ? WHERE id = ?",
            quantity, request.getEquipmentId());

        int quantityLeft = availableQty - quantity;
        Map<String, Object> result = new HashMap<>(booking);
        result.put("quantity_left", quantityLeft);
        return result;
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
                "FROM bookings b " +
                "JOIN equipment e ON b.equipment_id = e.id " +
                "WHERE b.id = ? AND b.user_id = ?",
                request.getBookingId(), userId);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Booking not found or not authorized");
        }

        LocalDate returnDate = LocalDate.parse(request.getReturnDate());
        Object endDateObj = booking.get("end_date");
        if (endDateObj != null) {
            LocalDate endDate = LocalDate.parse(endDateObj.toString());
            if (returnDate.isAfter(endDate)) {
                long lateDays = ChronoUnit.DAYS.between(endDate, returnDate);
                int penaltyPerDay = booking.get("penalty_per_day") != null
                    ? ((Number) booking.get("penalty_per_day")).intValue() : 0;
                int bookingQty = ((Number) booking.get("quantity")).intValue();
                int penaltyAmount = (int)(penaltyPerDay * lateDays * bookingQty);

                if (penaltyAmount > 0) {
                    // penalties table has: id, booking_id, late_days, penalty_amount
                    jdbcTemplate.update(
                        "INSERT INTO penalties (booking_id, late_days, penalty_amount) VALUES (?, ?, ?)",
                        request.getBookingId(), (int)lateDays, penaltyAmount);
                }
            }
        }

        jdbcTemplate.update(
            "UPDATE bookings SET status = 'completed' WHERE id = ?",
            request.getBookingId());

        int bookingQty = ((Number) booking.get("quantity")).intValue();
        jdbcTemplate.update(
            "UPDATE equipment SET quantity = quantity + ? WHERE id = ?",
            bookingQty, booking.get("equipment_id"));

        return jdbcTemplate.queryForMap("SELECT * FROM bookings WHERE id = ?", request.getBookingId());
    }
}
