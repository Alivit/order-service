package com.minispring.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemDto(
        Long id,
        String name,
        BigDecimal price,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt
) {
}
