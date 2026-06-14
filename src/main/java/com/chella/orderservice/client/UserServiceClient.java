package com.chella.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.chella.orderservice.client.fallback.UserServiceClientFallback;

@FeignClient(name = "user-service", url = "${USER_SERVICE_URL:http://localhost:8080}/api/users", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/{id}")
    Object getUserById(@RequestHeader("Authorization") String token, @PathVariable("id") Long id);
}
