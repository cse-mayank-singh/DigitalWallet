package com.loyaltyService.auth_service.client;

import com.loyaltyService.auth_service.client.UserQueryClient.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserQueryClientFallback implements UserQueryClient {
    @Override
    public UserProfileResponse getProfile(Long id) {
        log.error("user-service unavailable — cannot verify user status for userId={}", id);
        return null;
    }
}

