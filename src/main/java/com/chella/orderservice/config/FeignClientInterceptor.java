package com.chella.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
            
            if (authorizationHeader != null) {
                // Propagate the JWT token to downstream microservices!
                requestTemplate.header(AUTHORIZATION_HEADER, authorizationHeader);
            }
        }
    }
}
