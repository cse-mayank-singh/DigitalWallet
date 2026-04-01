package com.loyaltyService.wallet_service.service.impl;

import com.loyaltyService.wallet_service.entity.Payment;
import com.loyaltyService.wallet_service.repository.PaymentRepository;
import com.loyaltyService.wallet_service.service.RazorpayService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RazorpayServiceImpl implements RazorpayService {

    private final PaymentRepository paymentRepo;

    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;

    @Override
    public Order createOrder(Long userId, BigDecimal amount) throws RazorpayException {

        RazorpayClient client = new RazorpayClient(key, secret);

        JSONObject options = new JSONObject();
        options.put("amount", amount.multiply(BigDecimal.valueOf(100))); // paise
        options.put("currency", "INR");
        options.put("receipt", "wallet_" + System.currentTimeMillis());

        // ✅ CREATE ORDER FIRST
        Order order = client.orders.create(options);

        String orderId = order.get("id");
        // ✅ SAVE IN DB
        paymentRepo.save(
                Payment.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .amount(amount)
                        .status("CREATED")
                        .build()
        );

        return order;
    }
}