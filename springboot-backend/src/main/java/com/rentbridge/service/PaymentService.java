package com.rentbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentbridge.dto.CreateOrderRequest;
import com.rentbridge.dto.VerifyPaymentRequest;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
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

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> createOrder(CreateOrderRequest request) throws Exception {
        String currency = request.getCurrency() != null ? request.getCurrency() : "INR";
        long amountInPaise = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("amount", amountInPaise);
        orderData.put("currency", currency);
        orderData.put("receipt", "rcpt_" + System.currentTimeMillis());

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
            return result;
        }
    }

    public Map<String, Object> verifyPayment(VerifyPaymentRequest request) throws Exception {
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
        result.put("message", "Payment verified successfully");
        result.put("orderId", request.getRazorpayOrderId());
        result.put("paymentId", request.getRazorpayPaymentId());
        return result;
    }
}
