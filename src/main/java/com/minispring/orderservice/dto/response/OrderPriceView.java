package com.minispring.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderPriceView(UUID orderId, UUID userId, BigDecimal totalPrice) {}
