package com.chella.orderservice.service.impl;

import com.chella.orderservice.client.UserServiceClient;
import com.chella.orderservice.constant.OrderStatus;
import com.chella.orderservice.dto.request.CreateOrderRequest;
import com.chella.orderservice.dto.response.OrderResponse;
import com.chella.orderservice.entity.Order;
import com.chella.orderservice.event.OrderCreatedEventV1;
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
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Tracer tracer;
    
    private static final String TOPIC_ORDER_CREATED = "order-created-v1";

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        return Observation.createNotStarted("order.create", observationRegistry)
            .observe(() -> {
                // 1. Validate User exists (Synchronous via Feign with Resilience4j)
                try {
                    validateUser(request.getUserId()).join();
                } catch (Exception e) {
                    log.error("Failed to validate user: {}", e.getMessage());
                    throw new DownstreamServiceException("User service validation failed: " + e.getMessage());
                }

                // 2. Map and Save Order
                Order order = orderMapper.toEntity(request);
                order.setStatus(OrderStatus.CREATED);
                order.setNotificationStatus("PENDING"); // Notification is async now
                Order savedOrder = orderRepository.save(order);
                meterRegistry.counter("orders_created_total").increment();

                // 3. Trigger Notification via Kafka (Asynchronous Fire-and-Forget)
                publishOrderCreatedEvent(savedOrder);

                return orderMapper.toResponse(savedOrder);
            });
    }

    private void publishOrderCreatedEvent(Order order) {
        String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "unknown";
        
        OrderCreatedEventV1 event = OrderCreatedEventV1.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId(traceId)
                .orderId(order.getId())
                .userId(order.getUserId())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .status(order.getStatus().name())
                .timestamp(LocalDateTime.now())
                .build();
                
        // Produce to Kafka
        kafkaTemplate.send(TOPIC_ORDER_CREATED, String.valueOf(order.getId()), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("{\"event\":\"EVENT_PUBLISHED\", \"topic\":\"{}\", \"orderId\":\"{}\", \"traceId\":\"{}\"}", 
                            TOPIC_ORDER_CREATED, order.getId(), traceId);
                } else {
                    log.error("Failed to publish OrderCreatedEventV1 for Order ID: {}", order.getId(), ex);
                }
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

    // Fallbacks
    public CompletableFuture<Void> fallbackForUserService(Long userId, Throwable t) {
        log.error("CIRCUIT_BREAKER_OPEN / FALLBACK TRIGGERED for userService. Reason: {}", t.getMessage());
        return CompletableFuture.failedFuture(new CircuitBreakerOpenException("User service temporarily unavailable"));
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
