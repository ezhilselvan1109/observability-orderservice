package com.chella.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://localhost:8080/api/users")
public interface UserServiceClient {

    @GetMapping("/{id}")
    Object getUserById(@PathVariable("id") Long id);
}
