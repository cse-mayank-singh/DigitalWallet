package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.dto.UserProfileResponse;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.mapper.UserMapper;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import com.loyaltyService.user_service.service.UserQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * CQRS — Query implementation.
 * Handles all read operations for User.
 * Results are cached in Redis under "user-profile::{userId}" for 15 minutes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepo;
    private final KycRepository kycRepo;
    private final UserMapper userMapper;

    @Override
    @Cacheable(value = "user-profile", key = "#userId")
    public UserProfileResponse getProfile(Long userId) {
        log.debug("Cache miss — loading user profile from DB for userId={}", userId);
        return buildUserProfile(userId);
    }

    @Override
    @Cacheable(value = "user-profile", key = "#userId")
    public UserProfileResponse getUserProfile(Long userId) {
        return buildUserProfile(userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserProfileResponse buildUserProfile(Long userId) {
        User user = findUser(userId);
        String kycStatus = kycRepo
                .findFirstByUserIdOrderBySubmittedAtDesc(userId)
                .map(k -> k.getStatus().name())
                .orElse("NOT_SUBMITTED");
        return userMapper.toUserProfile(user, kycStatus);
    }

    private User findUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
