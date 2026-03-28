package com.loyaltyService.user_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthServiceClientFallback implements AuthServiceClient {

    @Override
    public void updateProfile(UpdateProfileRequest request) {
        log.error("auth-service unavailable — profile NOT synced for userId={}", request.userId());
    }
}