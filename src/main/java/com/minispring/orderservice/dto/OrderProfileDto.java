package com.minispring.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.minispring.orderservice.model.Status;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderProfileDto(
        UUID id,
        Status status,
        BigDecimal totalPrice,
        @JsonIgnore
        UUID userId,
        UserProfileDto user,
        Boolean userServiceAvailable,
        List<ItemDto> items,
        Instant createdAt,
        Instant updatedAt
) {
}
