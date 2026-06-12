package com.minispring.orderservice.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileView(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active,
        Boolean deleted,
        Instant createdAt,
        Instant updatedAt) {}
