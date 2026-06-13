package com.chella.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.chella.orderservice.client.fallback.UserServiceClientFallback;

@FeignClient(name = "user-service", url = "${USER_SERVICE_URL:http://localhost:8080}/api/users", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/{id}")
    Object getUserById(@PathVariable("id") Long id);
}
