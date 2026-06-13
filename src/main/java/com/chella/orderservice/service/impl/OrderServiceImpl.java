package com.chella.orderservice.service.impl;

import com.chella.orderservice.client.NotificationServiceClient;
import com.chella.orderservice.client.UserServiceClient;
import com.chella.orderservice.constant.OrderStatus;
import com.chella.orderservice.dto.request.CreateOrderRequest;
import com.chella.orderservice.dto.request.NotificationRequest;
import com.chella.orderservice.dto.response.OrderResponse;
import com.chella.orderservice.entity.Order;
import com.chella.orderservice.exception.NotificationFailedException;
import com.chella.orderservice.exception.OrderNotFoundException;
import com.chella.orderservice.exception.UserNotFoundException;
import com.chella.orderservice.mapper.OrderMapper;
import com.chella.orderservice.repository.OrderRepository;
import com.chella.orderservice.service.OrderService;
import feign.FeignException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        return Observation.createNotStarted("order.creation", observationRegistry)
            .observe(() -> {
                // 1. Validate User exists
                try {
                    userServiceClient.getUserById(request.getUserId());
                } catch (FeignException.NotFound e) {
                    throw new UserNotFoundException("User with ID " + request.getUserId() + " does not exist");
                } catch (FeignException e) {
                    log.error("Failed to connect to user service: ", e);
                    throw new RuntimeException("Failed to validate user due to external service error");
                }

                // 2. Map and Save Order
                Order order = orderMapper.toEntity(request);
                order.setStatus(OrderStatus.CREATED);
                Order savedOrder = orderRepository.save(order);
                meterRegistry.counter("orders_created_total").increment();

                // 3. Trigger Notification
                try {
                    NotificationRequest notificationRequest = new NotificationRequest(
                            savedOrder.getUserId(),
                            "Order Created Successfully"
                    );
                    notificationServiceClient.sendNotification(notificationRequest);
                } catch (Exception e) {
                    log.error("Failed to send notification for Order ID: {}", savedOrder.getId(), e);
                    // Non-blocking for order creation, but can throw depending on requirement
                    // throw new NotificationFailedException("Order created but failed to send notification");
                }

                return orderMapper.toResponse(savedOrder);
            });
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order with ID " + id + " not found"));
        return orderMapper.toResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());
    }
}
