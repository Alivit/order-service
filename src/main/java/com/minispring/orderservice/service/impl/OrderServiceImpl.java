package com.minispring.orderservice.service.impl;

import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderItemCreateDto;
import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import com.minispring.orderservice.mapper.OrderMapper;
import com.minispring.orderservice.model.Item;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.repository.ItemRepository;
import com.minispring.orderservice.repository.OrderRepository;
import com.minispring.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.minispring.orderservice.exception.ExceptionAnswer.ORDER_NOT_FOUND;

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
    public OrderProfileDto create(OrderCreateDto orderCreateDto, UserProfileDto user) {
        Order order = orderMapper.orderCreateDtoToOrder(orderCreateDto);
        order.setUserId(user.id());
        order.addItems(orderCreateDto.items(), getItemsMap(orderCreateDto));
        Order savedOrder = orderRepository.save(order);
        return orderMapper.orderToOrderProfileDto(savedOrder, user);
    }

    @Override
    public OrderProfileDto getById(UUID orderId, UserProfileDto user) {
        return orderMapper.orderToOrderProfileDto(getExistsOrderByIdIncludingDeleted(orderId), user);
    }

    @Override
    public OrderProfileDto getByIdAndUserId(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserIdAndDeletedFalse(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(ORDER_NOT_FOUND, orderId)));

        return orderMapper.orderToOrderProfileDto(order, null);
    }

    @Override
    public List<OrderProfileDto> getAllByUserId(UUID userId, UserProfileDto user, boolean includeDeleted) {
        return orderRepository.findAllByUserId(userId, includeDeleted)
                .stream()
                .map(order -> orderMapper.orderToOrderProfileDto(order, user))
                .toList();
    }

    @Override
    public Page<OrderProfileDto> getAllBy(OrderParamsDto orderParamsDto, Pageable pageable) {
        return orderRepository.findByParams(orderParamsDto, pageable)
                .map(order -> orderMapper.orderToOrderProfileDto(order, null));
    }

    @Transactional
    @Override
    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxRetries = 2, delay = 100)
    public OrderProfileDto update(UUID orderId, OrderUpdateDto orderUpdateDto) {
        Order order = getExistsOrderById(orderId);
        if (!order.getStatus().equals(orderUpdateDto.status())) {
            order.setStatus(orderUpdateDto.status());
            order.setUpdatedAt(Instant.now());
            log.info("Order with id {}, status has been changed to {}", orderId, orderUpdateDto.status());
        }
        return orderMapper.orderToOrderProfileDtoWithoutItems(order, null);
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

    private Order getExistsOrderByIdIncludingDeleted(UUID orderId) {
        return orderRepository.findOrderByIdIncludingDeleted(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
    }

    private Order getExistsOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ORDER_NOT_FOUND, orderId)));
    }

    private Map<Long, Item> getItemsMap(OrderCreateDto dto) {
        Set<Long> itemIds = dto.items().stream()
                .map(OrderItemCreateDto::itemId)
                .collect(Collectors.toSet());
        return itemRepository.findAllById(itemIds).stream().collect(Collectors.toMap(Item::getId, item -> item));
    }
}
