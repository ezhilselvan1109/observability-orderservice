package com.chella.orderservice.service.impl;

import com.chella.orderservice.client.NotificationServiceClient;
import com.chella.orderservice.client.UserServiceClient;
import com.chella.orderservice.constant.OrderStatus;
import com.chella.orderservice.dto.request.CreateOrderRequest;
import com.chella.orderservice.dto.request.NotificationRequest;
import com.chella.orderservice.dto.response.OrderResponse;
import com.chella.orderservice.entity.Order;
import com.chella.orderservice.exception.*;
import com.chella.orderservice.mapper.OrderMapper;
import com.chella.orderservice.repository.OrderRepository;
import com.chella.orderservice.service.OrderService;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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
        return Observation.createNotStarted("order.create", observationRegistry)
            .observe(() -> {
                // 1. Validate User exists (Resilience4j Protected)
                try {
                    validateUser(request.getUserId()).join();
                } catch (Exception e) {
                    log.error("Failed to validate user: {}", e.getMessage());
                    throw new DownstreamServiceException("User service validation failed: " + e.getMessage());
                }

                // 2. Map and Save Order
                Order order = orderMapper.toEntity(request);
                order.setStatus(OrderStatus.CREATED);
                order.setNotificationStatus("SENT");
                Order savedOrder = orderRepository.save(order);
                meterRegistry.counter("orders_created_total").increment();

                // 3. Trigger Notification (Resilience4j Protected)
                try {
                    NotificationRequest notificationRequest = new NotificationRequest(
                            savedOrder.getUserId(),
                            "Order Created Successfully"
                    );
                    sendNotificationAsync(savedOrder, notificationRequest).join();
                } catch (Exception e) {
                    log.error("Failed to send notification for Order ID: {}", savedOrder.getId(), e);
                }

                return orderMapper.toResponse(savedOrder);
            });
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackForUserService")
    @Retry(name = "userService")
    @Bulkhead(name = "userService", type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = "userService")
    public CompletableFuture<Void> validateUser(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                userServiceClient.getUserById(userId);
                return null;
            } catch (FeignException.NotFound | FeignException.BadRequest e) {
                // Do not retry 4xx errors
                throw new NonRetryableException("User with ID " + userId + " does not exist or invalid request.");
            } catch (FeignException e) {
                // Retry 5xx errors
                throw new RetryableException("Transient network glitch or 500 error from User Service: " + e.status());
            } catch (Exception e) {
                throw new RetryableException("Network timeout or unknown error: " + e.getMessage());
            }
        });
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackForNotificationService")
    @Retry(name = "notificationService")
    @Bulkhead(name = "notificationService", type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = "notificationService")
    public CompletableFuture<Void> sendNotificationAsync(Order order, NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                notificationServiceClient.sendNotification(request);
                return null;
            } catch (FeignException.BadRequest e) {
                throw new NonRetryableException("Invalid notification request format.");
            } catch (Exception e) {
                throw new RetryableException("Transient error sending notification: " + e.getMessage());
            }
        });
    }

    // Fallbacks
    public CompletableFuture<Void> fallbackForUserService(Long userId, Throwable t) {
        log.error("CIRCUIT_BREAKER_OPEN / FALLBACK TRIGGERED for userService. Reason: {}", t.getMessage());
        return CompletableFuture.failedFuture(new CircuitBreakerOpenException("User service temporarily unavailable"));
    }

    public CompletableFuture<Void> fallbackForNotificationService(Order order, NotificationRequest request, Throwable t) {
        log.warn("CIRCUIT_BREAKER_OPEN / FALLBACK TRIGGERED for notificationService. Order {} saved, marking notification as PENDING. Reason: {}", order.getId(), t.getMessage());
        order.setNotificationStatus("PENDING");
        orderRepository.save(order);
        return CompletableFuture.completedFuture(null);
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
