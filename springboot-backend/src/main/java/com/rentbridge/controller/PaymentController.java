package com.rentbridge.controller;

import com.rentbridge.dto.CreateOrderRequest;
import com.rentbridge.dto.VerifyPaymentRequest;
import com.rentbridge.model.UserContext;
import com.rentbridge.service.BookingService;
import com.rentbridge.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingService bookingService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request,
                                          HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            Map<String, Object> order = paymentService.createOrder(request, userContext.getUserId());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody VerifyPaymentRequest request,
                                            HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            Map<String, Object> result = paymentService.verifyPayment(request, userContext.getUserId());
            if (request.getBookingId() != null) {
                Map<String, Object> booking = bookingService.paymentSucceeded(
                    request.getBookingId(), userContext.getUserId(),
                    request.getRazorpayPaymentId(), request.getRazorpayOrderId());
                result.put("booking", booking);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
