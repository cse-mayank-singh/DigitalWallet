package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.dto.AdminDashboardResponse;
import com.loyaltyService.user_service.dto.AdminUserResponse;
import com.loyaltyService.user_service.entity.KycDetail;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import com.loyaltyService.user_service.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    private final UserRepository userRepo;
    private final KycRepository kycRepo;

    @Override
    public AdminDashboardResponse getDashboard() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfWeek = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        long total         = userRepo.count();
        long active        = userRepo.countByStatus(User.UserStatus.ACTIVE);
        long blocked       = userRepo.countByStatus(User.UserStatus.BLOCKED);
        long today         = userRepo.countByCreatedAtAfter(startOfDay);
        long week          = userRepo.countByCreatedAtAfter(startOfWeek);
        long month         = userRepo.countByCreatedAtAfter(startOfMonth);

        long regularUsers  = userRepo.countByRole(User.Role.USER);
        long adminUsers    = userRepo.countByRole(User.Role.ADMIN);
        long merchantUsers = userRepo.countByRole(User.Role.MERCHANT);

        long kycPending    = kycRepo.countByStatus(KycDetail.KycStatus.PENDING);
        long kycApproved   = kycRepo.countByStatus(KycDetail.KycStatus.APPROVED);
        long kycRejected   = kycRepo.countByStatus(KycDetail.KycStatus.REJECTED);
        long kycTotal      = kycPending + kycApproved + kycRejected;
        long kycNotSub     = total - kycTotal;

        long kycApprToday  = kycRepo.countByStatusSince(KycDetail.KycStatus.APPROVED, startOfDay);
        long kycRejToday   = kycRepo.countByStatusSince(KycDetail.KycStatus.REJECTED, startOfDay);

        return AdminDashboardResponse.builder()
                .totalUsers(total)
                .activeUsers(active)
                .blockedUsers(blocked)
                .newUsersToday(today)
                .newUsersThisWeek(week)
                .newUsersThisMonth(month)
                .regularUsers(regularUsers)
                .adminUsers(adminUsers)
                .merchantUsers(merchantUsers)
                .kycPending(kycPending)
                .kycApproved(kycApproved)
                .kycRejected(kycRejected)
                .kycNotSubmitted(Math.max(kycNotSub, 0))
                .kycApprovedToday(kycApprToday)
                .kycRejectedToday(kycRejToday)
                .build();
    }

    @Override
    public Page<AdminUserResponse> listUsers(Pageable pageable, User.UserStatus status, User.Role role) {
        Page<User> page;
        if (status != null && role != null) {
            page = userRepo.findByStatusAndRole(status, role, pageable);
        } else if (status != null) {
            page = userRepo.findByStatus(status, pageable);
        } else if (role != null) {
            page = userRepo.findByRole(role, pageable);
        } else {
            page = userRepo.findAll(pageable);
        }
        return page.map(this::toDto);
    }

    @Override
    public AdminUserResponse getUserById(Long userId) {
        User user = findOrThrow(userId);
        return toDto(user);
    }

    @Override
    public Page<AdminUserResponse> searchUsers(String keyword, Pageable pageable) {
        return userRepo.searchByKeyword(keyword, pageable).map(this::toDto);
    }

    @Override
    public AdminUserResponse findByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No user with email: " + email));
        return toDto(user);
    }

    @Override
    public AdminUserResponse findByPhone(String phone) {
        User user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("No user with phone: " + phone));
        return toDto(user);
    }

    @Override
    public Page<AdminUserResponse> findByDateRange(LocalDate from, LocalDate to, Pageable pageable) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return userRepo.findByCreatedAtBetween(fromInstant, toInstant, pageable).map(this::toDto);
    }

    @Override
    public Page<AdminUserResponse> findByKycStatus(String kycStatus, Pageable pageable) {
        return userRepo.findByLatestKycStatus(kycStatus.toUpperCase(Locale.ROOT), pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public AdminUserResponse setStatus(Long userId, User.UserStatus newStatus) {
        User user = findOrThrow(userId);
        user.setStatus(newStatus);
        userRepo.save(user);
        return toDto(user);
    }

    @Override
    @Transactional
    public AdminUserResponse changeRole(Long userId, User.Role newRole) {
        User user = findOrThrow(userId);
        user.setRole(newRole);
        userRepo.save(user);
        return toDto(user);
    }

    private User findOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private AdminUserResponse toDto(User user) {
        String kycStatus = kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(user.getId())
                .map(k -> k.getStatus().name())
                .orElse(NOT_SUBMITTED);

        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .role(user.getRole().name())
                .kycStatus(kycStatus)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .build();
    }
}
