package com.chella.orderservice.client.fallback;

import com.chella.orderservice.client.UserServiceClient;
import com.chella.orderservice.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Object getUserById(String token, Long id) {
        log.error("Circuit breaker triggered! User service is down. Failed to fetch user with id: {}", id);
        // Throwing an exception allows the order service to reject the order gracefully 
        // instead of hanging or returning a 500 error blindly.
        throw new UserNotFoundException("User service is currently unavailable. Please try again later.");
    }
}
