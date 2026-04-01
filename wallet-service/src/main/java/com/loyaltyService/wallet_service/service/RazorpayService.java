package com.loyaltyService.wallet_service.service;

import java.math.BigDecimal;

import com.razorpay.Order;
import com.razorpay.RazorpayException;

public interface RazorpayService {
	Order createOrder(Long userId, BigDecimal amount)throws RazorpayException;
}
