package com.spry.order.order.dto;

import com.spry.order.order.Order;
import com.spry.order.order.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    OrderResponse toResponse(Order order);

    @Mapping(target = "itemCount", expression = "java(order.getItems().size())")
    OrderSummaryResponse toSummary(Order order);

    OrderItemResponse toItemResponse(OrderItem item);
}
