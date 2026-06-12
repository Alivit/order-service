package com.minispring.orderservice.messaging.kafka.listener;

import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.messaging.event.PaymentCreatedEvent;
import com.minispring.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventConsumer {

    private final OrderService orderService;

    @RetryableTopic(
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {ResourceNotFoundException.class, IllegalArgumentException.class})
    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(PaymentCreatedEvent event, Acknowledgment ack) {
        log.info("Received Payment Event: OrderId = {}, Status = {}", event.orderId(), event.status());
        orderService.processPayment(event.orderId(), event.status());
        ack.acknowledge();
        log.debug("Successfully processed and committed offset for OrderId: {}", event.orderId());
    }
}
