package com.loyaltyService.wallet_service.controller;

import com.loyaltyService.wallet_service.entity.Payment;
import com.loyaltyService.wallet_service.repository.PaymentRepository;
import com.loyaltyService.wallet_service.service.WalletCommandService;
import com.loyaltyService.wallet_service.service.WalletQueryService;
import com.loyaltyService.wallet_service.service.RazorpayService;
import com.razorpay.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.MessageDigest;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

        private final PaymentRepository paymentRepo;
        private final RazorpayService razorpayService;
        private final WalletCommandService walletCommandService;
        private final WalletQueryService walletQueryService;
        private final com.loyaltyService.wallet_service.service.KafkaProducerService kafkaProducer;

        @Value("${razorpay.secret}")
        private String secret;

        @PostMapping("/create-order")
        public ResponseEntity<?> createOrder(
                        @RequestHeader("X-User-Id") Long userId,
                        @RequestParam BigDecimal amount) throws Exception {

                Order order = razorpayService.createOrder(userId, amount);
                return ResponseEntity.ok(Map.of(
                                "orderId", order.get("id"),
                                "amount(paise)", order.get("amount"),
                                "currency", order.get("currency")));
        }

        @PostMapping("/verify")
        public ResponseEntity<?> verify(
                        @RequestHeader("X-User-Id") Long userId,
                        @RequestBody Map<String, String> payload) throws Exception {

                String orderId = payload.get("razorpayOrderId");
                String paymentId = payload.get("razorpayPaymentId");
                String signature = payload.get("razorpaySignature");

                String generatedSignature = hmacSha256(orderId + "|" + paymentId, secret);

                if (!MessageDigest.isEqual(
                                generatedSignature.getBytes(),
                                signature.getBytes())) {
                        return ResponseEntity.badRequest().body("Invalid signature");
                }

                Payment payment = paymentRepo.findById(orderId)
                                .orElseThrow(() -> new RuntimeException("Order not found"));

                BigDecimal amount = payment.getAmount(); // ✅ real amount

                walletCommandService.topup(userId, amount, orderId);
                payment.setStatus("SUCCESS");
                paymentRepo.save(payment);
                BigDecimal updatedBalance = walletQueryService.getBalance(userId).getBalance();

                kafkaProducer.send("payment-events", Map.of(
                                "event", "PAYMENT_SUCCESS",
                                "userId", userId,
                                "amount", amount,
                                "orderId", orderId,
                                "balance", updatedBalance // 👈 fetch from wallet
                ));

                return ResponseEntity.ok("Payment verified & wallet credited");
        }

        // ✅ ADD THIS METHOD
        public String hmacSha256(String data, String secret) throws Exception {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
                byte[] rawHmac = mac.doFinal(data.getBytes());
                return org.apache.commons.codec.binary.Hex.encodeHexString(rawHmac);
        }
}