package com.minispring.orderservice.service;

import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.OrderPriceView;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.dto.response.UserProfileView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderView create(OrderCreateRequest orderCreateRequest, UserProfileView user);

    OrderView getById(UUID orderId, UserProfileView user);

    OrderView getByIdAndUserId(UUID orderId, UUID userId);

    List<OrderView> getAllByUserId(UUID userId, UserProfileView user, boolean includeDeleted);

    OrderPriceView getOrderPriceById(UUID orderId);

    Page<OrderView> getAllBy(OrderSearchCriteria orderSearchCriteria, Pageable pageable);

    OrderView update(UUID orderId, OrderUpdateRequest orderUpdateRequest);

    void delete(UUID orderId, UUID userId);

    void delete(UUID orderId);

    void processPayment(UUID orderId, String paymentStatus);
}
