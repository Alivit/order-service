package com.minispring.orderservice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        Boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
}
