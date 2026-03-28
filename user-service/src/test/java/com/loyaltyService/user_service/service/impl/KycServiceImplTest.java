package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.client.WalletServiceClient;
import com.loyaltyService.user_service.dto.KycStatusResponse;
import com.loyaltyService.user_service.entity.AuditLog;
import com.loyaltyService.user_service.entity.KycDetail;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.DuplicateKycException;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.repository.AuditLogRepository;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import com.loyaltyService.user_service.service.KafkaProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceImplTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private KycRepository kycRepo;

    @Mock
    private AuditLogRepository auditRepo;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private KafkaProducerService kafkaProducer;

    @InjectMocks
    private KycServiceImpl kycService;

    @Test
    void submitKycWithoutFilePersistsPendingRecordAndAuditLog() {
        User user = user(10L);
        KycDetail savedKyc = pendingKyc(100L, user);

        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(kycRepo.existsByUserIdAndStatus(10L, KycDetail.KycStatus.APPROVED)).thenReturn(false);
        when(kycRepo.save(any(KycDetail.class))).thenReturn(savedKyc);

        KycStatusResponse response = kycService.submitKyc(10L, KycDetail.DocType.PAN, "ABCDE1234F", null);

        assertEquals(100L, response.getKycId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("PAN", response.getDocType());

        ArgumentCaptor<KycDetail> kycCaptor = ArgumentCaptor.forClass(KycDetail.class);
        verify(kycRepo).save(kycCaptor.capture());
        assertEquals("ABCDE1234F", kycCaptor.getValue().getDocNumber());
        assertEquals(KycDetail.KycStatus.PENDING, kycCaptor.getValue().getStatus());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepo).save(auditCaptor.capture());
        assertEquals("KYC_SUBMITTED", auditCaptor.getValue().getAction());
        assertEquals(10L, auditCaptor.getValue().getUserId());
    }

    @Test
    void submitKycThrowsWhenApprovedKycAlreadyExists() {
        when(userRepo.findById(10L)).thenReturn(Optional.of(user(10L)));
        when(kycRepo.existsByUserIdAndStatus(10L, KycDetail.KycStatus.APPROVED)).thenReturn(true);

        assertThrows(DuplicateKycException.class,
                () -> kycService.submitKyc(10L, KycDetail.DocType.PAN, "ABCDE1234F", null));
        verify(kycRepo, never()).save(any(KycDetail.class));
    }

    @Test
    void submitKycThrowsWhenUserMissing() {
        when(userRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> kycService.submitKyc(999L, KycDetail.DocType.PAN, "ABCDE1234F", null));
    }

    @Test
    void submitKycWrapsStorageFailure() {
        User user = user(10L);
        MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        ReflectionTestUtils.setField(kycService, "uploadDir", "target/test-uploads");

        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(kycRepo.existsByUserIdAndStatus(10L, KycDetail.KycStatus.APPROVED)).thenReturn(false);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("doc.png");
        try {
            when(file.getInputStream()).thenThrow(new java.io.IOException("disk full"));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> kycService.submitKyc(10L, KycDetail.DocType.PAN, "ABCDE1234F", file));

        assertEquals("Failed to store KYC document", exception.getMessage());
        verify(kycRepo, never()).save(any(KycDetail.class));
    }

    @Test
    void getStatusReturnsLatestKyc() {
        KycDetail kyc = pendingKyc(200L, user(10L));
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(10L)).thenReturn(Optional.of(kyc));

        KycStatusResponse response = kycService.getStatus(10L);

        assertEquals(200L, response.getKycId());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void getStatusThrowsWhenNoSubmissionExists() {
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> kycService.getStatus(10L));
    }

    @Test
    void getPendingKycMapsRepositoryPage() {
        KycDetail kyc = pendingKyc(300L, user(10L));
        when(kycRepo.findByStatusOrderBySubmittedAtDesc(eq(KycDetail.KycStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(kyc), PageRequest.of(0, 10), 1));

        var page = kycService.getPendingKyc(PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("PENDING", page.getContent().getFirst().getStatus());
    }

    @Test
    void approveByUserIdMarksApprovedPublishesEventAndCreatesWallet() {
        User user = user(10L);
        KycDetail pending = pendingKyc(400L, user);
        when(kycRepo.findFirstByUserIdAndStatusOrderBySubmittedAtDesc(10L, KycDetail.KycStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(kycRepo.save(any(KycDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycStatusResponse response = kycService.approveByUserId(10L, "admin@example.com");

        assertEquals("APPROVED", response.getStatus());
        assertEquals("admin@example.com", pending.getReviewedBy());
        verify(walletServiceClient).createWallet(10L);
        verify(auditRepo).save(any(AuditLog.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaProducer).send(eq("kyc-events"), eventCaptor.capture());
        assertEquals(Map.of(
                "event", "KYC_APPROVED",
                "userId", 10L,
                "reference", "KYC_400",
                "status", "APPROVED"
        ), eventCaptor.getValue());
    }

    @Test
    void approveThrowsWhenAlreadyApproved() {
        KycDetail approved = pendingKyc(401L, user(10L));
        approved.setStatus(KycDetail.KycStatus.APPROVED);
        when(kycRepo.findById(401L)).thenReturn(Optional.of(approved));

        assertThrows(DuplicateKycException.class, () -> kycService.approve(401L, "admin@example.com"));
    }

    @Test
    void approveThrowsWhenKycIsNotPending() {
        KycDetail rejected = pendingKyc(402L, user(10L));
        rejected.setStatus(KycDetail.KycStatus.REJECTED);
        when(kycRepo.findById(402L)).thenReturn(Optional.of(rejected));

        assertThrows(IllegalStateException.class, () -> kycService.approve(402L, "admin@example.com"));
    }

    @Test
    void approveThrowsWhenKycMissing() {
        when(kycRepo.findById(403L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> kycService.approve(403L, "admin@example.com"));
    }

    @Test
    void rejectByUserIdMarksRejectedAndPublishesEvent() {
        User user = user(10L);
        KycDetail pending = pendingKyc(500L, user);
        when(kycRepo.findFirstByUserIdAndStatusOrderBySubmittedAtDesc(10L, KycDetail.KycStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(kycRepo.save(any(KycDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycStatusResponse response = kycService.rejectByUserId(10L, "blurred image", "admin@example.com");

        assertEquals("REJECTED", response.getStatus());
        assertEquals("blurred image", pending.getRejectionReason());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaProducer).send(eq("kyc-events"), eventCaptor.capture());
        assertEquals(Map.of(
                "event", "KYC_REJECTED",
                "userId", 10L,
                "reference", "KYC_500",
                "status", "REJECTED",
                "reason", "blurred image"
        ), eventCaptor.getValue());
    }

    @Test
    void rejectByKycIdMarksRejectedAndSavesAudit() {
        User user = user(10L);
        KycDetail pending = pendingKyc(501L, user);
        when(kycRepo.findById(501L)).thenReturn(Optional.of(pending));
        when(kycRepo.save(any(KycDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycStatusResponse response = kycService.reject(501L, "mismatch", "admin@example.com");

        assertEquals("REJECTED", response.getStatus());
        verify(auditRepo).save(any(AuditLog.class));
        verify(kafkaProducer).send(eq("kyc-events"), any());
    }

    @Test
    void rejectThrowsWhenKycIsNotPending() {
        KycDetail approved = pendingKyc(502L, user(10L));
        approved.setStatus(KycDetail.KycStatus.APPROVED);
        when(kycRepo.findById(502L)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class, () -> kycService.reject(502L, "reason", "admin@example.com"));
    }

    @Test
    void approveByUserIdThrowsWhenPendingKycMissing() {
        when(kycRepo.findFirstByUserIdAndStatusOrderBySubmittedAtDesc(10L, KycDetail.KycStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> kycService.approveByUserId(10L, "admin@example.com"));
    }

    @Test
    void rejectByUserIdThrowsWhenPendingKycMissing() {
        when(kycRepo.findFirstByUserIdAndStatusOrderBySubmittedAtDesc(10L, KycDetail.KycStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> kycService.rejectByUserId(10L, "reason", "admin@example.com"));
    }

    @Test
    void approveStillReturnsSuccessWhenWalletCreationFails() {
        User user = user(10L);
        KycDetail pending = pendingKyc(600L, user);
        when(kycRepo.findById(600L)).thenReturn(Optional.of(pending));
        when(kycRepo.save(any(KycDetail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("wallet down")).when(walletServiceClient).createWallet(10L);

        KycStatusResponse response = kycService.approve(600L, "admin@example.com");

        assertEquals("APPROVED", response.getStatus());
        verify(kafkaProducer).send(eq("kyc-events"), any());
    }

    @Test
    void submitKycWithFileStoresDocument() {
        User user = user(10L);
        KycDetail savedKyc = pendingKyc(700L, user);
        MockMultipartFile file = new MockMultipartFile("file", "doc.png", "image/png", "content".getBytes());
        ReflectionTestUtils.setField(kycService, "uploadDir", "target/test-uploads");

        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(kycRepo.existsByUserIdAndStatus(10L, KycDetail.KycStatus.APPROVED)).thenReturn(false);
        when(kycRepo.save(any(KycDetail.class))).thenReturn(savedKyc);

        KycStatusResponse response = kycService.submitKyc(10L, KycDetail.DocType.PASSPORT, "P123456", file);

        assertNotNull(response);
        verify(kycRepo).save(any(KycDetail.class));
    }

    @Test
    void countPendingDelegatesToRepository() {
        when(kycRepo.countByStatus(KycDetail.KycStatus.PENDING)).thenReturn(4L);

        assertEquals(4L, kycService.countPending());
    }

    @Test
    void countApprovedTodayDelegatesToRepository() {
        when(kycRepo.countByStatusSince(eq(KycDetail.KycStatus.APPROVED), any())).thenReturn(3L);

        assertEquals(3L, kycService.countApprovedToday());
    }

    @Test
    void countRejectedTodayDelegatesToRepository() {
        when(kycRepo.countByStatusSince(eq(KycDetail.KycStatus.REJECTED), any())).thenReturn(2L);

        assertEquals(2L, kycService.countRejectedToday());
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email("test@example.com")
                .phone("9999999999")
                .role(User.Role.USER)
                .status(User.UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();
    }

    private KycDetail pendingKyc(Long id, User user) {
        return KycDetail.builder()
                .id(id)
                .user(user)
                .docType(KycDetail.DocType.PAN)
                .docNumber("ABCDE1234F")
                .status(KycDetail.KycStatus.PENDING)
                .submittedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:30:00Z"))
                .build();
    }
}
