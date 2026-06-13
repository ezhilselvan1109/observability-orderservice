package com.chella.orderservice.client.fallback;

import com.chella.orderservice.client.NotificationServiceClient;
import com.chella.orderservice.dto.request.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void sendNotification(NotificationRequest request) {
        log.error("Circuit breaker triggered! Notification service is down. Failed to send notification for user id: {}", request.getUserId());
        // Do not throw an exception here. We want the order to succeed even if notifications fail!
        log.warn("Order was created, but notification was suppressed due to service unavailability.");
    }
}
