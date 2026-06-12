package com.minispring.orderservice.dto;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record OrderParamsDto(

        @Size(max = 5, message = "The number of statuses must not exceed {max}")
        List<String> statuses,

        @PastOrPresent(message = "The start date must be in the past or present")
        Instant createdAtFrom,

        @PastOrPresent(message = "The end date must be in the past or present")
        Instant createdAtTo,

        Boolean includeDeleted
) {
    public OrderParamsDto {
        if (createdAtFrom != null && createdAtTo != null && createdAtFrom.isAfter(createdAtTo)) {
            throw new IllegalArgumentException("The start date (createdAtFrom) cannot be after the end date (createdAtTo)");
        }

        if (statuses == null) {
            statuses = List.of();
        }

        if (includeDeleted == null) {
            includeDeleted = false;
        }
    }
}
