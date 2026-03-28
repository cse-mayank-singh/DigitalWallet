package com.loyaltyService.auth_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {
    @Override
    public void createUser(CreateUserRequest request) {
        log.error("user-service unavailable — user profile NOT created for id={}", request.id());
    }
}
