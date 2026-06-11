package com.minispring.orderservice.service;

import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.dto.UserProfileDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderProfileDto create(OrderCreateDto orderCreateDto, UserProfileDto user);

    OrderProfileDto getById(UUID orderId, UserProfileDto user);

    List<OrderProfileDto> getAllByUserId(UUID userId, UserProfileDto user, boolean includeDeleted);

    Page<OrderProfileDto> getAllBy(OrderParamsDto orderParamsDto, Pageable pageable);

    OrderProfileDto update(UUID orderId, OrderUpdateDto orderUpdateDto, UserProfileDto user);

    void delete(UUID orderId, UUID userId);

}
