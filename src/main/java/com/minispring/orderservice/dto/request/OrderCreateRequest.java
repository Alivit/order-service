package com.minispring.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 100, message = "Order cannot contain more than {max} unique items")
        List<@Valid OrderItemCreateRequest> items) {
    public OrderCreateRequest {
        items = (items != null) ? List.copyOf(items) : List.of();
    }
}
