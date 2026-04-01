package com.loyaltyService.wallet_service.service;

import com.loyaltyService.wallet_service.entity.Payment;
import com.loyaltyService.wallet_service.repository.PaymentRepository;
import com.loyaltyService.wallet_service.service.impl.RazorpayServiceImpl;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceTest {

    @Mock
    private PaymentRepository paymentRepo;

    @InjectMocks
    private RazorpayServiceImpl razorpayService;

    @Test
    void createOrderWithoutCredentialsThrows() {
        ReflectionTestUtils.setField(razorpayService, "key", null);
        ReflectionTestUtils.setField(razorpayService, "secret", null);

        assertThrows(Exception.class, () -> razorpayService.createOrder(1L, new BigDecimal("100.00")));
    }
}
