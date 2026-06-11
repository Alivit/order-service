package com.minispring.orderservice.dto;

import com.minispring.orderservice.model.Status;

public record OrderUpdateDto(
      Status status
) {
}
