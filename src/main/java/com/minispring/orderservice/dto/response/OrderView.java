package com.minispring.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.minispring.orderservice.model.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record OrderView(
        UUID id,
        Status status,
        BigDecimal totalPrice,
        @JsonIgnore UUID userId,
        UserProfileView user,
        Boolean userServiceAvailable,
        List<ItemView> items,
        Instant createdAt,
        Instant updatedAt) {}
