package com.minispring.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemView(
        Long id, String name, BigDecimal price, Integer quantity, Instant createdAt, Instant updatedAt) {}
