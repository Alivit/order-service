package com.minispring.orderservice.service.impl;

import static com.minispring.orderservice.exception.ExceptionAnswer.ORDER_NOT_FOUND;

import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderItemCreateRequest;
import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.OrderPriceView;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.OrderMapper;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.Status;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import com.minispring.orderservice.service.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderView create(OrderCreateRequest orderCreateRequest, UserProfileView user) {
        Order order = orderMapper.toEntity(orderCreateRequest);
        order.setUserId(user.id());
        order.addItems(orderCreateRequest.items(), getItemsMap(orderCreateRequest));
        Order savedOrder = orderRepository.save(order);
        log.info("Order {} successfully created by user {}", savedOrder.getId(), user.id());
        return orderMapper.toView(savedOrder, user);
    }

    @Override
    public OrderView getById(UUID orderId, UserProfileView user) {
        return orderMapper.toView(getExistsOrderByIdIncludingDeleted(orderId), user);
    }

    @Override
    public OrderView getByIdAndUserId(UUID orderId, UUID userId) {
        Order order = orderRepository
                .findByIdAndUserIdAndDeletedFalse(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));

        return orderMapper.toView(order, null);
    }

    @Override
    public List<OrderView> getAllByUserId(UUID userId, UserProfileView user, boolean includeDeleted) {
        return orderRepository.findAllByUserId(userId, includeDeleted).stream()
                .map(order -> orderMapper.toView(order, user))
                .toList();
    }

    @Override
    public OrderPriceView getOrderPriceById(UUID orderId) {
        return orderRepository
                .findOrderPriceByIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
    }

    @Override
    public Page<OrderView> getAllBy(OrderSearchCriteria orderSearchCriteria, Pageable pageable) {
        return orderRepository
                .findByParams(orderSearchCriteria, pageable)
                .map(order -> orderMapper.toView(order, null));
    }

    @Transactional
    @Override
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            backoff = @Backoff(delay = 100, multiplier = 2.0))
    public OrderView update(UUID orderId, OrderUpdateRequest orderUpdateRequest) {
        Order order = getExistsActiveOrderById(orderId);
        if (!order.getStatus().equals(orderUpdateRequest.status())) {
            order.setStatus(orderUpdateRequest.status());
            log.info("Order with id {}, status has been changed to {}", orderId, orderUpdateRequest.status());
            order = orderRepository.saveAndFlush(order);
        }
        return orderMapper.toView(order, null);
    }

    @Transactional
    @Override
    public void delete(UUID orderId, UUID userId) {
        int deletedRows = orderRepository.deleteOrderByIdAndUserId(orderId, userId, Instant.now());
        if (deletedRows == 0) {
            throw new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId));
        }
        log.info("Order {} soft-deleted by user {}", orderId, userId);
    }

    @Transactional
    @Override
    public void delete(UUID orderId) {
        int deletedRows = orderRepository.deleteOrderById(orderId, Instant.now());
        if (deletedRows == 0) {
            throw new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId));
        }
        log.info("Order {} soft-deleted by admin", orderId);
    }

    @Transactional
    @Override
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            backoff = @Backoff(delay = 100, multiplier = 2.0))
    public void processPayment(UUID orderId, String paymentStatus) {
        Order order = getExistsActiveOrderById(orderId);
        if (order.getStatus() == Status.PAID) {
            log.warn("Order {} is already paid or canceled - {}", orderId, order.getStatus());
            return;
        }

        switch (paymentStatus) {
            case "SUCCESS" -> {
                order.setStatus(Status.PAID);
                log.info("Order {} status changed to PAID", orderId);
            }
            case "FAILED", "REJECTED" -> {
                order.setStatus(Status.CANCELED);
                log.info("Order {} status changed to CANCELED", orderId);
            }
            default -> log.warn("Received unknown payment status: {} for order {}", paymentStatus, orderId);
        }
    }

    private Order getExistsOrderByIdIncludingDeleted(UUID orderId) {
        return orderRepository
                .findOrderByIdIncludingDeleted(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
    }

    private Order getExistsActiveOrderById(UUID orderId) {
        return orderRepository
                .findByIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
    }

    private Map<Long, Item> getItemsMap(OrderCreateRequest dto) {
        Set<Long> itemIds =
                dto.items().stream().map(OrderItemCreateRequest::itemId).collect(Collectors.toSet());
        return itemRepository.findAllById(itemIds).stream().collect(Collectors.toMap(Item::getId, item -> item));
    }
}
