package com.minispring.orderservice.util.validation;

import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, OrderSearchCriteria> {

    @Override
    public boolean isValid(
            OrderSearchCriteria orderSearchCriteria, ConstraintValidatorContext constraintValidatorContext) {
        if (orderSearchCriteria == null) {
            return true;
        }

        Instant from =
                orderSearchCriteria.createdAtFrom() != null ? orderSearchCriteria.createdAtFrom() : Instant.EPOCH;
        Instant to = orderSearchCriteria.createdAtTo() != null ? orderSearchCriteria.createdAtTo() : Instant.now();

        return !from.isAfter(to);
    }
}
