package com.chella.orderservice.service;

import com.chella.orderservice.dto.request.CreateOrderRequest;
import com.chella.orderservice.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(Long id);
    List<OrderResponse> getOrdersByUserId(Long userId);
    List<OrderResponse> getAllOrders();
}
