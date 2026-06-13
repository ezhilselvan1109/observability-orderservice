package com.chella.orderservice.client;

import com.chella.orderservice.dto.request.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.chella.orderservice.client.fallback.NotificationServiceClientFallback;

@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL:http://localhost:8082}/api/notifications", fallback = NotificationServiceClientFallback.class)
public interface NotificationServiceClient {

    @PostMapping
    void sendNotification(@RequestBody NotificationRequest request);
}
