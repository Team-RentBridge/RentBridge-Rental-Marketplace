package com.rentbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentbridge.dto.CreateOrderRequest;
import com.rentbridge.dto.VerifyPaymentRequest;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> createOrder(CreateOrderRequest request, Long userId) throws Exception {
        String currency = request.getBookingId() != null || request.getCurrency() == null
            ? "INR" : request.getCurrency();
        BigDecimal amount = request.getAmount();
        if (request.getBookingId() != null) {
            Map<String, Object> booking;
            try {
                booking = jdbcTemplate.queryForMap(
                    "SELECT total_price, status, hold_expires_at > NOW() AS hold_active " +
                    "FROM bookings WHERE id = ? AND user_id = ?",
                    request.getBookingId(), userId);
            } catch (EmptyResultDataAccessException e) {
                throw new RuntimeException("Booking hold not found");
            }
            if (!"payment_pending".equals(booking.get("status"))
                    || !Boolean.TRUE.equals(booking.get("hold_active"))) {
                throw new RuntimeException("Booking hold expired; choose the dates again");
            }
            amount = decimalValue(booking.get("total_price"), "Booking total");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("amount", amountInPaise);
        orderData.put("currency", currency);
        orderData.put("receipt", request.getBookingId() == null
            ? "rcpt_" + System.currentTimeMillis()
            : "booking_" + request.getBookingId() + "_" + System.currentTimeMillis());
        if (request.getBookingId() != null) {
            orderData.put("notes", Map.of("booking_id", request.getBookingId().toString()));
        }

        String jsonBody = objectMapper.writeValueAsString(orderData);

        String credentials = razorpayKeyId + ":" + razorpayKeySecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        Request httpRequest = new Request.Builder()
                .url("https://api.razorpay.com/v1/orders")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Razorpay error: " + responseBody);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            if (request.getBookingId() != null) {
                int updated = jdbcTemplate.update(
                    "UPDATE bookings SET payment_order_id = ? " +
                    "WHERE id = ? AND user_id = ? AND status = 'payment_pending' AND hold_expires_at > NOW()",
                    result.get("id"), request.getBookingId(), userId);
                if (updated == 0) {
                    throw new RuntimeException("Booking hold expired before the payment order was created");
                }
            }
            return result;
        }
    }

    public Map<String, Object> verifyPayment(VerifyPaymentRequest request, Long userId) throws Exception {
        if (request.getBookingId() != null) {
            Map<String, Object> booking;
            try {
                booking = jdbcTemplate.queryForMap(
                    "SELECT payment_order_id FROM bookings WHERE id = ? AND user_id = ?",
                    request.getBookingId(), userId);
            } catch (EmptyResultDataAccessException e) {
                throw new RuntimeException("Booking hold not found");
            }
            if (!request.getRazorpayOrderId().equals(booking.get("payment_order_id"))) {
                throw new RuntimeException("Payment order does not belong to this booking");
            }
        }
        String data = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        String expectedSignature = hexString.toString();
        boolean isValid = expectedSignature.equals(request.getRazorpaySignature());

        if (!isValid) {
            throw new RuntimeException("Invalid payment signature");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("signatureIsValid", "true");
        result.put("message", "Payment verified successfully");
        result.put("orderId", request.getRazorpayOrderId());
        result.put("paymentId", request.getRazorpayPaymentId());
        return result;
    }

    public void refundPayment(Long bookingId, String paymentId) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(
            Map.of("notes", Map.of("booking_id", bookingId.toString())));
        String credentials = razorpayKeyId + ":" + razorpayKeySecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));

        Request request = new Request.Builder()
            .url("https://api.razorpay.com/v1/payments/" + paymentId + "/refund")
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .header("Authorization", "Basic " + encodedCredentials)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "rentbridge-booking-refund-" + bookingId)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Razorpay refund failed: " + responseBody);
            }
        }
    }

    private BigDecimal decimalValue(Object value, String label) {
        if (!(value instanceof Number)) {
            throw new RuntimeException(label + " is not configured correctly");
        }
        return new BigDecimal(value.toString());
    }
}
