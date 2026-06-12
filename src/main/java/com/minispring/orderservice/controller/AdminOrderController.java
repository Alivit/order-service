package com.minispring.orderservice.controller;

import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.request.OrderSearchCriteria;
import com.minispring.orderservice.dto.request.OrderUpdateRequest;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.service.facade.OrderFacade;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ResponseEntity<OrderView> create(
            @Valid @RequestBody OrderCreateRequest request, @RequestParam(value = "userEmail") String userEmail) {
        OrderView response = orderFacade.createOrder(request, userEmail);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> getOrderById(
            @PathVariable UUID id, @RequestParam(value = "userEmail") String userEmail) {
        return ResponseEntity.ok(orderFacade.getOrderByIdAndEmail(id, userEmail));
    }

    @GetMapping
    public ResponseEntity<Page<OrderView>> getAllOrders(
            @Valid OrderSearchCriteria params, @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(orderFacade.getAllOrdersBy(params, pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderView>> getUserOrders(
            @PathVariable UUID userId,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        return ResponseEntity.ok(orderFacade.getAllOrdersByUserId(userId, includeDeleted));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderView> updateOrderStatus(
            @PathVariable UUID id, @Valid @RequestBody OrderUpdateRequest orderUpdateRequest) {
        return ResponseEntity.ok(orderFacade.updateOrderStatus(id, orderUpdateRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderFacade.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
