package com.minispring.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateDto(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a well-formed email address")
        String email,

        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 100, message = "Order cannot contain more than {max} unique items")
        List<@Valid OrderItemCreateDto> items
) {
}
