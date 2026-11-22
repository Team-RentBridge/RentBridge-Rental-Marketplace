package com.rentbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateFulfillmentStatusRequest {
    @JsonProperty("delivery_status")
    private String deliveryStatus;

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
}
