package com.minispring.orderservice.mapper;

import com.minispring.grpc.order.OrderPriceDto;
import com.minispring.orderservice.dto.request.OrderCreateRequest;
import com.minispring.orderservice.dto.response.ItemView;
import com.minispring.orderservice.dto.response.OrderPriceView;
import com.minispring.orderservice.dto.response.OrderView;
import com.minispring.orderservice.dto.response.UserProfileView;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.OrderItem;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "status", constant = "CREATED")
    Order toEntity(OrderCreateRequest request);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "updatedAt", source = "order.updatedAt")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "userServiceAvailable", expression = "java(user != null)")
    @Mapping(target = "items", source = "order.items")
    OrderView toView(Order order, UserProfileView user);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "name", source = "item.name")
    @Mapping(target = "price", source = "item.price")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "createdAt", source = "item.createdAt")
    @Mapping(target = "updatedAt", source = "item.updatedAt")
    ItemView toItemView(OrderItem orderItem);

    default OrderPriceDto toGrpcOrderPriceDto(OrderPriceView view) {
        if (view == null) {
            return null;
        }
        return OrderPriceDto.newBuilder()
                .setOrderId(view.orderId().toString())
                .setUserId(view.userId().toString())
                .setTotalPrice(view.totalPrice().toString())
                .build();
    }
}
