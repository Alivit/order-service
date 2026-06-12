package com.minispring.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemCreateDto(
        @NotNull(message = "Item ID must not be null")
        Long itemId,

        @NotNull(message = "Quantity must not be null")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity
) {
}
