package com.rentbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddReviewRequest {
    @JsonProperty("equipment_id")
    private Long equipmentId;

    private Integer rating;
    private String comment;

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
