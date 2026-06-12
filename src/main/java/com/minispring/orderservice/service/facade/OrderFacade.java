package com.minispring.orderservice.service.facade;

import com.minispring.orderservice.client.UserGrpClient;
import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.exception.ServiceUnavailableException;
import com.minispring.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final UserGrpClient userGrpcService;

    public OrderProfileDto createOrder(OrderCreateDto orderCreateDto, String email) {
        UserProfileDto user = userGrpcService.getUserByEmail(email);
        if (user == null) {
            throw new ServiceUnavailableException("User service unavailable");
        }
        return orderService.create(orderCreateDto, user);
    }

    public OrderProfileDto getOrderByIdAndEmail(UUID orderId, String email) {
        UserProfileDto user = userGrpcService.getUserByEmail(email);
        return orderService.getById(orderId, user);
    }

    public OrderProfileDto getOrderByIdAndUserId(UUID orderId, UUID userId) {
        OrderProfileDto order = orderService.getByIdAndUserId(orderId, userId);
        UserProfileDto user = userGrpcService.getUserById(userId);

        return order.toBuilder().user(user).userServiceAvailable(user != null).build();
    }

    public List<OrderProfileDto> getAllOrdersByUserId(UUID userId, boolean includeDeleted) {
        UserProfileDto user = userGrpcService.getUserById(userId);
        return orderService.getAllByUserId(userId, user, includeDeleted);
    }

    public Page<OrderProfileDto> getAllOrdersBy(OrderParamsDto orderDto, Pageable pageable) {
        Page<OrderProfileDto> dtoPage = orderService.getAllBy(orderDto, pageable);

        if (dtoPage.isEmpty()) {
            return dtoPage;
        }

        Set<UUID> userIds = dtoPage.getContent().stream()
                .map(OrderProfileDto::userId)
                .collect(Collectors.toSet());

        Map<UUID, UserProfileDto> usersMap = userGrpcService.getUsersByIds(userIds);

        return dtoPage.map(dto -> {
            UserProfileDto user = usersMap.get(dto.userId());
            return dto.toBuilder().user(user).userServiceAvailable(user != null).build();
        });
    }

    public OrderProfileDto updateOrderStatus(UUID orderId, OrderUpdateDto orderUpdateDto) {
        OrderProfileDto order = orderService.update(orderId, orderUpdateDto);
        UserProfileDto user = userGrpcService.getUserById(order.userId());
        return order.toBuilder().user(user).userServiceAvailable(user != null).build();
    }

    public void deleteOrder(UUID orderId, UUID userId) {
        orderService.delete(orderId, userId);
    }

    public void deleteOrder(UUID orderId) {
        orderService.delete(orderId);
    }

}
