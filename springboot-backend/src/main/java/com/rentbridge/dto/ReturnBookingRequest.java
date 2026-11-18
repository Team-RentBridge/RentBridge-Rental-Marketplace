package com.rentbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReturnBookingRequest {
    @JsonProperty("booking_id")
    private Long bookingId;

    @JsonProperty("return_date")
    private String returnDate;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
}
