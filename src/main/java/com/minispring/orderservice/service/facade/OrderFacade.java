package com.minispring.orderservice.service.facade;

import com.minispring.orderservice.client.UserGrpcClient;
import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.exception.ServiceUnavailableException;
import com.minispring.orderservice.service.OrderService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final UserGrpcClient userGrpcService;

    public OrderView createOrder(OrderCreateRequest orderCreateRequest, String email) {
        UserProfileView user = userGrpcService.getUserByEmail(email);
        if (user == null) {
            throw new ServiceUnavailableException("User service unavailable");
        }
        return orderService.create(orderCreateRequest, user);
    }

    public OrderView getOrderByIdAndEmail(UUID orderId, String email) {
        UserProfileView user = userGrpcService.getUserByEmail(email);
        return orderService.getById(orderId, user);
    }

    public OrderView getOrderByIdAndUserId(UUID orderId, UUID userId) {
        OrderView order = orderService.getByIdAndUserId(orderId, userId);
        UserProfileView user = userGrpcService.getUserById(userId);

        return order.toBuilder().user(user).userServiceAvailable(user != null).build();
    }

    public List<OrderView> getAllOrdersByUserId(UUID userId, boolean includeDeleted) {
        UserProfileView user = userGrpcService.getUserById(userId);
        return orderService.getAllByUserId(userId, user, includeDeleted);
    }

    public Page<OrderView> getAllOrdersBy(OrderSearchCriteria orderDto, Pageable pageable) {
        Page<OrderView> dtoPage = orderService.getAllBy(orderDto, pageable);

        if (dtoPage.isEmpty()) {
            return dtoPage;
        }

        Set<UUID> userIds = dtoPage.getContent().stream().map(OrderView::userId).collect(Collectors.toSet());

        Map<UUID, UserProfileView> usersMap = userGrpcService.getUsersByIds(userIds);

        return dtoPage.map(dto -> {
            UserProfileView user = usersMap.get(dto.userId());
            return dto.toBuilder().user(user).userServiceAvailable(user != null).build();
        });
    }

    public OrderView updateOrderStatus(UUID orderId, OrderUpdateRequest orderUpdateRequest) {
        OrderView order = orderService.update(orderId, orderUpdateRequest);
        UserProfileView user = userGrpcService.getUserById(order.userId());
        return order.toBuilder().user(user).userServiceAvailable(user != null).build();
    }

    public void deleteOrder(UUID orderId, UUID userId) {
        orderService.delete(orderId, userId);
    }

    public void deleteOrder(UUID orderId) {
        orderService.delete(orderId);
    }
}
