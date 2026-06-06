package com.minispring.orderservice.dto;

import com.minispring.orderservice.model.Status;

import java.time.Instant;
import java.util.List;

public record OrderParamsDto(
        List<String> statuses,
        Instant createdAtFrom,
        Instant createdAtTo
) {
}
