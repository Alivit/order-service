package com.minispring.orderservice.dto.request;

import com.minispring.orderservice.model.Status;
import jakarta.validation.constraints.NotNull;

public record OrderUpdateRequest(
        @NotNull(message = "Status cannot be null") Status status) {}
