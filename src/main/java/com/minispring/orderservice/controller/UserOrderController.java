package com.minispring.orderservice.controller;

import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.service.facade.OrderFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/orders")
@RequiredArgsConstructor
public class UserOrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ResponseEntity<OrderProfileDto> create(@Valid @RequestBody OrderCreateDto request,
                                                  @RequestHeader("TokenEmail") String email){
        OrderProfileDto response = orderFacade.createOrder(request, email);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderProfileDto> getOrderById(@PathVariable UUID id,
                                                        @RequestHeader("TokenId") UUID userId
    ) {
        return ResponseEntity.ok(orderFacade.getOrderByIdAndUserId(id, userId));
    }

    @GetMapping
    public ResponseEntity<List<OrderProfileDto>> getUserOrders(@RequestHeader("TokenId") UUID userId) {
        return ResponseEntity.ok(orderFacade.getAllOrdersByUserId(userId, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestHeader("TokenId") UUID userId) {
        orderFacade.deleteOrder(id, userId);
        return ResponseEntity.noContent().build();
    }
}
