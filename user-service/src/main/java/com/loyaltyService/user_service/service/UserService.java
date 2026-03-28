package com.loyaltyService.user_service.service;

import com.loyaltyService.user_service.dto.UpdateUserRequest;
import com.loyaltyService.user_service.dto.UserProfileResponse;
import com.loyaltyService.user_service.entity.User;


public interface UserService {
    UserProfileResponse getProfile(Long userId);
    UserProfileResponse updateProfile(Long userId, UpdateUserRequest req);

    void createUser(Long id, String name, String email, String phone, User.Role role);
}
