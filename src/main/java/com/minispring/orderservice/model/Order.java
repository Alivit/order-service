package com.minispring.orderservice.model;

import static com.minispring.orderservice.exception.ExceptionAnswer.ITEM_NOT_FOUND;

import com.minispring.orderservice.dto.request.OrderItemCreateRequest;
import com.minispring.orderservice.exception.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Version
    private Long version;

    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "user_id")
    private UUID userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItems(List<OrderItemCreateRequest> dtoList, Map<Long, Item> itemsMap) {
        this.totalPrice = BigDecimal.ZERO;
        this.items.clear();

        for (OrderItemCreateRequest dto : dtoList) {
            Item item = itemsMap.get(dto.itemId());
            if (item == null) {
                throw new ResourceNotFoundException(String.format(ITEM_NOT_FOUND, dto.itemId()));
            }

            this.items.add(new OrderItem(this, item, dto.quantity()));

            this.totalPrice = this.totalPrice.add(item.getPrice().multiply(BigDecimal.valueOf(dto.quantity())));
        }
    }
}
