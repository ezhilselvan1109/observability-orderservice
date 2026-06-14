package com.chella.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEventV1 {
    private String eventId;
    private String traceId;
    private Long orderId;
    private Long userId;
    private String productName;
    private Integer quantity;
    private String status;
    private LocalDateTime timestamp;
}
