package com.rentbridge.dto;

import java.math.BigDecimal;

public class CreateOrderRequest {
    private BigDecimal amount;
    private String currency = "INR";

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
