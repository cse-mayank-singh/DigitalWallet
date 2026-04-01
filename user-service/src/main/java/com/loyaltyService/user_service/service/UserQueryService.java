package com.loyaltyService.user_service.service;

import com.loyaltyService.user_service.dto.UserProfileResponse;

/**
 * CQRS — Query side: all read operations for User.
 * Results are cached in Redis.
 */
public interface UserQueryService {

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse getUserProfile(Long userId);
}
