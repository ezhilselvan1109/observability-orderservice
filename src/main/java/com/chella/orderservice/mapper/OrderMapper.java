package com.chella.orderservice.mapper;

import com.chella.orderservice.dto.request.CreateOrderRequest;
import com.chella.orderservice.dto.response.OrderResponse;
import com.chella.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(CreateOrderRequest request);

    OrderResponse toResponse(Order order);
}
