package com.minispring.orderservice.mapper;

import com.minispring.orderservice.dto.ItemDto;
import com.minispring.orderservice.dto.OrderCreateDto;
import com.minispring.orderservice.dto.OrderProfileDto;
import com.minispring.orderservice.dto.UserProfileDto;
import com.minispring.orderservice.model.Order;
import com.minispring.orderservice.model.OrderItem;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;


@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface OrderMapper {

    @Mapping(target = "status", constant = "CREATED")
    @Mapping(target = "items", ignore = true)
    Order orderCreateDtoToOrder(OrderCreateDto orderCreateDto);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "updatedAt", source = "order.updatedAt")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "userServiceAvailable", expression = "java(user != null)")
    OrderProfileDto orderToOrderProfileDto(Order order, UserProfileDto user);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "updatedAt", source = "order.updatedAt")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "userServiceAvailable", expression = "java(user != null)")
    @Mapping(target = "items", ignore = true)
    OrderProfileDto orderToOrderProfileDtoWithoutItems(Order order, UserProfileDto user);

    @Mapping(target = "id", source = "item.id")
    @Mapping(target = "name", source = "item.name")
    @Mapping(target = "price", source = "item.price")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "createdAt", source = "item.createdAt")
    @Mapping(target = "updatedAt", source = "item.updatedAt")
    ItemDto orderItemToItemDto(OrderItem orderItem);

}
