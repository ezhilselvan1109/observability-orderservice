package com.chella.orderservice.client;

import com.chella.orderservice.dto.request.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "http://localhost:8082/api/notifications")
public interface NotificationServiceClient {

    @PostMapping
    void sendNotification(@RequestBody NotificationRequest request);
}
