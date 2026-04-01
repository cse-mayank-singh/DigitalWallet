package com.loyaltyService.auth_service.client;

import com.loyaltyService.auth_service.client.UserQueryClient.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        contextId = "userQueryClient",
        fallback = UserQueryClientFallback.class
)public interface UserQueryClient {

    @GetMapping("/api/users/internal/users/{id}")
    UserProfileResponse getProfile(@PathVariable Long id);

    record UserProfileResponse(
            Long id,
            String name,
            String email,
            String phone,
            String status,
            String kycStatus,
            java.time.Instant createdAt
    ) {}
}

