package com.minispring.orderservice.controller;

import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.service.facade.OrderFacade;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("api/v1/orders")
@RequiredArgsConstructor
public class UserOrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ResponseEntity<OrderView> create(
            @Valid @RequestBody OrderCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        OrderView response = orderFacade.createOrder(request, jwt.getClaimAsString("email"));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> getOrderById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(orderFacade.getOrderByIdAndUserId(id, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping
    public ResponseEntity<List<OrderView>> getUserOrders(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(orderFacade.getAllOrdersByUserId(UUID.fromString(jwt.getSubject()), false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        orderFacade.deleteOrder(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
