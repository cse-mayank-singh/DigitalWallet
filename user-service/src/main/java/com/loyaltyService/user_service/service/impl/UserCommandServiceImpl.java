package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.client.AuthServiceClient;
import com.loyaltyService.user_service.dto.UpdateUserRequest;
import com.loyaltyService.user_service.dto.UserProfileResponse;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.mapper.UserMapper;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import com.loyaltyService.user_service.service.UserCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS — Command implementation.
 * Handles all write (state-changing) operations for User.
 * Evicts the Redis cache on every mutation so that the query-side
 * always returns fresh data after a write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepo;
    private final KycRepository kycRepo;
    private final AuthServiceClient authServiceClient;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void createUser(Long id, String name, String email, String phone, User.Role role) {
        if (userRepo.existsById(id)) {
            log.info("User already exists: id={}", id);
            return;
        }
        User user = User.builder()
                .id(id)
                .name(name)
                .email(email)
                .phone(phone)
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .build();
        userRepo.save(user);
        log.info("User created: id={}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "user-profile", key = "#userId")
    public UserProfileResponse updateProfile(Long userId, UpdateUserRequest req) {
        User user = findUser(userId);
        userMapper.updateUserFromDto(req, user);
        userRepo.save(user);

        // Sync with Auth Service
        authServiceClient.updateProfile(
                new AuthServiceClient.UpdateProfileRequest(userId, req.getName(), req.getPhone()));

        log.info("Profile updated and cache evicted for userId={}", userId);
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
