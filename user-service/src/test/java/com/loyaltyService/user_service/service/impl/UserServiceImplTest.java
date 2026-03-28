package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.client.AuthServiceClient;
import com.loyaltyService.user_service.dto.UpdateUserRequest;
import com.loyaltyService.user_service.dto.UserProfileResponse;
import com.loyaltyService.user_service.entity.KycDetail;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private KycRepository kycRepo;

    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getProfileReturnsUserWithLatestKycStatus() {
        User user = user(1L);
        KycDetail kyc = KycDetail.builder()
                .id(20L)
                .user(user)
                .docType(KycDetail.DocType.PAN)
                .docNumber("ABCDE1234F")
                .status(KycDetail.KycStatus.APPROVED)
                .build();

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.of(kyc));

        UserProfileResponse response = userService.getProfile(1L);

        assertEquals(1L, response.getId());
        assertEquals("Test User", response.getName());
        assertEquals("APPROVED", response.getKycStatus());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void updateProfileSavesUserCallsAuthClientAndReturnsUpdatedProfile() {
        User user = user(1L);
        UpdateUserRequest request = new UpdateUserRequest("Updated Name", "9999999999");

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.empty());

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertEquals("Updated Name", response.getName());
        assertEquals("9999999999", response.getPhone());
        assertEquals("NOT_SUBMITTED", response.getKycStatus());
        verify(userRepo).save(user);
        verify(authServiceClient).updateProfile(new AuthServiceClient.UpdateProfileRequest(1L, "Updated Name", "9999999999"));
    }

    @Test
    void updateProfileLeavesExistingValuesWhenRequestFieldsAreNull() {
        User user = user(1L);
        UpdateUserRequest request = new UpdateUserRequest(null, null);

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.empty());

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertEquals("Test User", response.getName());
        assertEquals("9876543210", response.getPhone());
        verify(authServiceClient).updateProfile(new AuthServiceClient.UpdateProfileRequest(1L, null, null));
    }

    @Test
    void createUserSkipsWhenAlreadyPresent() {
        when(userRepo.existsById(5L)).thenReturn(true);

        userService.createUser(5L, "Existing", "existing@example.com", "8888888888", User.Role.USER);

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void createUserSavesNewActiveUser() {
        when(userRepo.existsById(6L)).thenReturn(false);

        userService.createUser(6L, "New User", "new@example.com", "7777777777", User.Role.ADMIN);

        verify(userRepo).save(argThat(user ->
                user.getId().equals(6L)
                        && user.getName().equals("New User")
                        && user.getEmail().equals("new@example.com")
                        && user.getPhone().equals("7777777777")
                        && user.getRole() == User.Role.ADMIN
                        && user.getStatus() == User.UserStatus.ACTIVE));
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile(99L));
    }

    @Test
    void updateProfileThrowsWhenUserMissing() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfile(99L, new UpdateUserRequest("Name", "9999999999")));
        verify(userRepo, never()).save(any(User.class));
        verify(authServiceClient, never()).updateProfile(any());
    }

    @Test
    void getUserProfileDelegatesToGetProfile() {
        User user = user(7L);
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(7L)).thenReturn(Optional.empty());

        UserProfileResponse response = userService.getUserProfile(7L);

        assertEquals(7L, response.getId());
        assertEquals("NOT_SUBMITTED", response.getKycStatus());
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email("test@example.com")
                .phone("9876543210")
                .role(User.Role.USER)
                .status(User.UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }
}
