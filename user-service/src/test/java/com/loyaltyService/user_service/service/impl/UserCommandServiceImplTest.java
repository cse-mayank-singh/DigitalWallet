package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.client.AuthServiceClient;
import com.loyaltyService.user_service.dto.UpdateUserRequest;
import com.loyaltyService.user_service.dto.UserProfileResponse;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.mapper.UserMapper;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCommandServiceImplTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private KycRepository kycRepo;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserCommandServiceImpl userCommandService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("John")
                .email("john@example.com")
                .phone("1234567890")
                .role(User.Role.USER)
                .build();
    }

    @Test
    void testCreateUser_NotExists() {
        when(userRepo.existsById(1L)).thenReturn(false);

        userCommandService.createUser(1L, "John", "john@example.com", "1234567890", User.Role.USER);

        verify(userRepo, times(1)).save(any(User.class));
    }

    @Test
    void testCreateUser_AlreadyExists() {
        when(userRepo.existsById(1L)).thenReturn(true);

        userCommandService.createUser(1L, "John", "john@example.com", "1234567890", User.Role.USER);

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void testUpdateProfile() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setName("John Updated");
        req.setPhone("0987654321");

        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.empty());

        UserProfileResponse resMock = new UserProfileResponse();
        resMock.setName("John Updated");

        when(userMapper.toUserProfile(testUser, "NOT_SUBMITTED")).thenReturn(resMock);

        UserProfileResponse res = userCommandService.updateProfile(1L, req);

        verify(userMapper, times(1)).updateUserFromDto(req, testUser);
        verify(userRepo, times(1)).save(testUser);
        verify(authServiceClient, times(1)).updateProfile(any(AuthServiceClient.UpdateProfileRequest.class));

        assertNotNull(res);
        assertEquals("John Updated", res.getName());
    }
}
