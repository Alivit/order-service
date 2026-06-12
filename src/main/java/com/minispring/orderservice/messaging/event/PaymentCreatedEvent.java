package com.minispring.orderservice.messaging.event;

import java.util.UUID;

public record PaymentCreatedEvent(UUID paymentId, UUID orderId, String status) {}
