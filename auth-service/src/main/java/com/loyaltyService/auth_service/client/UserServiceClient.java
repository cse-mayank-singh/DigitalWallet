package com.loyaltyService.auth_service.client;

import com.loyaltyService.auth_service.model.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {
    @PostMapping("/api/users/internal/create")
    void createUser(@RequestBody CreateUserRequest request);

    record CreateUserRequest(Long id, String name, String email, String phone, User.Role role) {}
}
