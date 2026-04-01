package com.loyaltyService.user_service.service.impl;

import com.loyaltyService.user_service.client.WalletServiceClient;
import com.loyaltyService.user_service.dto.KycStatusResponse;
import com.loyaltyService.user_service.entity.AuditLog;
import com.loyaltyService.user_service.entity.KycDetail;
import com.loyaltyService.user_service.entity.User;
import com.loyaltyService.user_service.exception.DuplicateKycException;
import com.loyaltyService.user_service.exception.ResourceNotFoundException;
import com.loyaltyService.user_service.mapper.KycMapper;
import com.loyaltyService.user_service.repository.AuditLogRepository;
import com.loyaltyService.user_service.repository.KycRepository;
import com.loyaltyService.user_service.repository.UserRepository;
import com.loyaltyService.user_service.service.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @Mock
    private KycMapper kycMapper;

    @InjectMocks
    private KycServiceImpl kycService;

    private User testUser;
    private KycDetail testKyc;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").build();
        testKyc = KycDetail.builder().id(100L).user(testUser).status(KycDetail.KycStatus.PENDING).build();
        ReflectionTestUtils.setField(kycService, "uploadDir", "target/test-uploads");
    }

    @Test
    void testSubmitKyc_Success() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(kycRepo.existsByUserIdAndStatus(1L, KycDetail.KycStatus.APPROVED)).thenReturn(false);
        when(kycRepo.save(any(KycDetail.class))).thenReturn(testKyc);
        
        KycStatusResponse resMock = new KycStatusResponse();
        resMock.setStatus("PENDING");
        when(kycMapper.toResponse(any())).thenReturn(resMock);

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());

        KycStatusResponse res = kycService.submitKyc(1L, KycDetail.DocType.PAN, "ID12345", file);

        assertNotNull(res);
        assertEquals("PENDING", res.getStatus());
        verify(auditRepo, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testSubmitKyc_AlreadyApproved() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
        when(kycRepo.existsByUserIdAndStatus(1L, KycDetail.KycStatus.APPROVED)).thenReturn(true);

        assertThrows(DuplicateKycException.class, () -> 
            kycService.submitKyc(1L, KycDetail.DocType.PAN, "ID123", null));
    }

    @Test
    void testGetStatus_Success() {
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.of(testKyc));
        KycStatusResponse resMock = new KycStatusResponse();
        resMock.setStatus("PENDING");
        when(kycMapper.toResponse(testKyc)).thenReturn(resMock);

        KycStatusResponse res = kycService.getStatus(1L);
        assertNotNull(res);
        assertEquals("PENDING", res.getStatus());
    }

    @Test
    void testGetStatus_NotFound() {
        when(kycRepo.findFirstByUserIdOrderBySubmittedAtDesc(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> kycService.getStatus(1L));
    }
}
