package com.minispring.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemCreateDto(
        @NotNull
        Long itemId,

        @NotNull
        @Min(value = 1)
        Integer quantity
) {
}
