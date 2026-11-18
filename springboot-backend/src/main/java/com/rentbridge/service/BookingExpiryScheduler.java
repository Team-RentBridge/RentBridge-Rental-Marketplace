package com.rentbridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BookingExpiryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private volatile boolean applicationReady;

    public BookingExpiryScheduler(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markApplicationReady() {
        applicationReady = true;
    }

    @Scheduled(fixedDelay = 10000)
    public void expireBookingsAndRefundDeclinedReservations() {
        if (!applicationReady) {
            return;
        }
        bookingService.expireBookings();
        List<Map<String, Object>> refunds = bookingService.claimPendingRefunds();
        for (Map<String, Object> refund : refunds) {
            Long bookingId = ((Number) refund.get("id")).longValue();
            String paymentId = (String) refund.get("payment_id");
            try {
                paymentService.refundPayment(bookingId, paymentId);
                bookingService.setRefundStatus(bookingId, "refunded");
            } catch (Exception e) {
                bookingService.setRefundStatus(bookingId, "failed");
                logger.error("Automatic refund failed for booking {}", bookingId, e);
            }
        }
    }
}
