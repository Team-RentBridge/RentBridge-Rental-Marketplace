package com.rentbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class AddEquipmentRequest {
    private String title;
    private String description;

    @JsonProperty("price_per_day")
    private BigDecimal pricePerDay;

    @JsonProperty("penalty_per_day")
    private BigDecimal penaltyPerDay;

    private String category;

    @JsonProperty("available_from")
    private String availableFrom;

    @JsonProperty("available_to")
    private String availableTo;

    @JsonProperty("image_url")
    private String imageUrl;

    private Integer quantity;

    @JsonProperty("is_featured")
    private Boolean isFeatured;

    @JsonProperty("is_for_sale")
    private Boolean isForSale;

    @JsonProperty("instant_booking")
    private Boolean instantBooking;

    @JsonProperty("buy_price")
    private BigDecimal buyPrice;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPricePerDay() { return pricePerDay; }
    public void setPricePerDay(BigDecimal pricePerDay) { this.pricePerDay = pricePerDay; }

    public BigDecimal getPenaltyPerDay() { return penaltyPerDay; }
    public void setPenaltyPerDay(BigDecimal penaltyPerDay) { this.penaltyPerDay = penaltyPerDay; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(String availableFrom) { this.availableFrom = availableFrom; }

    public String getAvailableTo() { return availableTo; }
    public void setAvailableTo(String availableTo) { this.availableTo = availableTo; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsFeatured() { return isFeatured; }
    public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }

    public Boolean getIsForSale() { return isForSale; }
    public void setIsForSale(Boolean isForSale) { this.isForSale = isForSale; }

    public Boolean getInstantBooking() { return instantBooking; }
    public void setInstantBooking(Boolean instantBooking) { this.instantBooking = instantBooking; }

    public BigDecimal getBuyPrice() { return buyPrice; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
}
