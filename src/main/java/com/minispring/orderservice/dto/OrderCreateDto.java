package com.minispring.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateDto(
        @Email
        @NotBlank
        String email,

        @Size(min = 1)
        List<@Valid OrderItemCreateDto> items
) {
}
