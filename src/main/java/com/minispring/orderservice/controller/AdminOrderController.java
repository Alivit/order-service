package com.minispring.orderservice.controller;

import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderParamsDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.OrderUpdateDto;
import com.minispring.orderservice.service.facade.OrderFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ResponseEntity<OrderProfileDto> create(@Valid @RequestBody OrderCreateDto request,
                                                  @RequestParam(value = "userEmail") String userEmail){
        OrderProfileDto response = orderFacade.createOrder(request, userEmail);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderProfileDto> getOrderById(@PathVariable UUID id,
                                                        @RequestParam(value = "userEmail") String userEmail
    ) {
        return ResponseEntity.ok(orderFacade.getOrderByIdAndEmail(id, userEmail));
    }

    @GetMapping
    public ResponseEntity<Page<OrderProfileDto>> getAllOrders(
            OrderParamsDto params,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(orderFacade.getAllOrdersBy(params, pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderProfileDto>> getUserOrders(
            @PathVariable UUID userId,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted
    ) {
        return ResponseEntity.ok(orderFacade.getAllOrdersByUserId(userId, includeDeleted));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderProfileDto> updateOrderStatus(@PathVariable UUID id,
                                                             @Valid @RequestBody OrderUpdateDto orderUpdateDto
    ) {
        return ResponseEntity.ok(orderFacade.updateOrderStatus(id, orderUpdateDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderFacade.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
