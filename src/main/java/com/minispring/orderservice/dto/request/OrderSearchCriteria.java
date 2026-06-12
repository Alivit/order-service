package com.minispring.orderservice.dto.request;

import com.minispring.orderservice.util.validation.ValidDateRange;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@ValidDateRange
public record OrderSearchCriteria(
        @Size(max = 5, message = "The number of statuses must not exceed {max}")
        List<String> statuses,

        @PastOrPresent(message = "The start date must be in the past or present")
        Instant createdAtFrom,

        @PastOrPresent(message = "The end date must be in the past or present")
        Instant createdAtTo,

        Boolean includeDeleted) {
    public OrderSearchCriteria {
        statuses = (statuses == null) ? List.of() : List.copyOf(statuses);
        includeDeleted = (includeDeleted != null) ? includeDeleted : false;
    }
}
