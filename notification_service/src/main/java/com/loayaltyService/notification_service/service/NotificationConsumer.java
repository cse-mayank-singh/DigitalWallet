//package com.loayaltyService.notification_service.service;
//
//import com.loayaltyService.notification_service.client.UserClient;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Service;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class NotificationConsumer {
//
//    private final EmailService emailService;
//    private final UserClient userClient;
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    @KafkaListener(topics = "wallet-events", groupId = "notification-group")
//    public void walletEvents(String event) {
//        processEvent(event);
//    }
//
//    @KafkaListener(topics = "payment-events", groupId = "notification-group")
//    public void paymentEvents(String event) {
//        processEvent(event);
//    }
//
//    @KafkaListener(topics = "reward-events", groupId = "notification-group")
//    public void rewardEvents(String event) {
//        processEvent(event);
//    }
//
//    @KafkaListener(topics = "kyc-events", groupId = "notification-group")
//    public void kycEvents(String event) {
//        processEvent(event);
//    }
//
//    private void processEvent(String event) {
//
//        log.info("🔥 RAW EVENT: {}", event);
//
//        try {
//            Map<String, Object> data = mapper.readValue(event, Map.class);
//            String eventType = (String) data.get("event");
//
//            switch (eventType) {
//
//                // ================= KYC =================
//                case "KYC_APPROVED":
//                case "KYC_REJECTED":
//
//                    Long kycUserId = Long.valueOf(data.get("userId").toString());
//                    String email = userClient.getProfile(kycUserId).getEmail();
//
//                    String subject = eventType.equals("KYC_APPROVED")
//                            ? "KYC Approved ✅"
//                            : "KYC Rejected ❌";
//
//                    String message = eventType.equals("KYC_APPROVED")
//                            ? "Your KYC has been successfully approved. You can now use all features."
//                            : "Your KYC has been rejected. Reason: " + data.get("reason");
//
//                    String body = buildEmailHtml(
//                            subject,
//                            message,
//                            "-", "-", "KYC-" + kycUserId
//                    );
//
//                    emailService.sendHtml(email, subject, body);
//
//                    log.info("KYC email sent to {}", email);
//                    break;
//
//                // ================= TRANSFER =================
//                case "TRANSFER_SUCCESS":
//
//                    Long senderId = Long.valueOf(data.get("senderId").toString());
//                    Long receiverId = Long.valueOf(data.get("receiverId").toString());
//
//                    String senderEmail = userClient.getProfile(senderId).getEmail();
//                    String receiverEmail = userClient.getProfile(receiverId).getEmail();
//
//                    String amount = String.valueOf(data.get("amount"));
//                    String balance = String.valueOf(data.get("balance"));
//                    String reference = String.valueOf(data.getOrDefault("reference", "N/A"));
//
//                    emailService.sendHtml(senderEmail, "Transfer Successful",
//                            buildEmailHtml("Money Sent", "You have successfully sent money.", amount, balance, reference));
//
//                    emailService.sendHtml(receiverEmail, "Money Received",
//                            buildEmailHtml("Money Received", "You have received money.", amount, balance, reference));
//
//                    break;
//
//                // ================= OTHER EVENTS =================
//                case "TOPUP_SUCCESS":
//                case "WITHDRAW_SUCCESS":
//                case "PAYMENT_SUCCESS":
//                case "POINTS_EARNED":
//                case "REDEEM_SUCCESS":
//
//                    Long userId = Long.valueOf(data.get("userId").toString());
//                    String userEmail = userClient.getProfile(userId).getEmail();
//
//                    String amt = String.valueOf(data.get("amount"));
//                    String bal = String.valueOf(data.get("balance"));
//                    String ref = String.valueOf(data.getOrDefault("reference", "N/A"));
//
//                    String subjectOther = getSubject(eventType);
//                    String messageOther = getMessage(eventType);
//
//                    emailService.sendHtml(userEmail, subjectOther,
//                            buildEmailHtml(subjectOther, messageOther, amt, bal, ref));
//
//                    break;
//            }
//
//        } catch (Exception e) {
//            log.error("Error processing Kafka event", e);
//        }
//    }
//
//    private String getSubject(String eventType) {
//        return switch (eventType) {
//            case "TOPUP_SUCCESS" -> "Wallet Top-up Confirmation";
//            case "WITHDRAW_SUCCESS" -> "Withdrawal Confirmation";
//            case "PAYMENT_SUCCESS" -> "Payment Confirmation";
//            case "POINTS_EARNED" -> "Reward Points Earned";
//            case "REDEEM_SUCCESS" -> "Points Redemption Successful";
//            default -> "Notification";
//        };
//    }
//
//    private String getMessage(String eventType) {
//        return switch (eventType) {
//            case "TOPUP_SUCCESS" -> "Your wallet has been successfully credited.";
//            case "WITHDRAW_SUCCESS" -> "Amount has been debited from your wallet.";
//            case "PAYMENT_SUCCESS" -> "Your payment has been processed successfully.";
//            case "POINTS_EARNED" -> "You have earned reward points.";
//            case "REDEEM_SUCCESS" -> "Your reward points have been redeemed.";
//            default -> "Transaction update.";
//        };
//    }
//
//    private String buildEmailHtml(String title, String message,
//                                  String amount, String balance, String reference) {
//
//        return "<!DOCTYPE html>" +
//                "<html><body style='font-family:Arial;background:#f4f6f8;padding:20px;'>" +
//                "<div style='max-width:600px;margin:auto;background:white;border-radius:10px;overflow:hidden;'>" +
//
//                "<div style='background:#2c3e50;color:white;padding:20px;text-align:center;'>" +
//                "<h2>Loyalty Wallet</h2></div>" +
//
//                "<div style='padding:25px;'>" +
//                "<h3>" + title + "</h3>" +
//                "<p>" + message + "</p>" +
//
//                "<p><b>Amount:</b> ₹" + amount + "</p>" +
//                "<p><b>Reference:</b> " + reference + "</p>" +
//                "<p><b>Balance:</b> ₹" + balance + "</p>" +
//
//                "</div></div></body></html>";
//    }
//}
package com.loayaltyService.notification_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loayaltyService.notification_service.client.UserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final EmailService emailService;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wallet-events", groupId = "notification-group")
    public void walletEvents(String event) {
        processEvent(event);
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void paymentEvents(String event) {
        processEvent(event);
    }

    @KafkaListener(topics = "reward-events", groupId = "notification-group")
    public void rewardEvents(String event) {
        processEvent(event);
    }

    @KafkaListener(topics = "kyc-events", groupId = "notification-group")
    public void kycEvents(String event) {
        processEvent(event);
    }

    @SuppressWarnings("unchecked")
    private void processEvent(String event) {
        log.info("Received Kafka event: {}", event);
        try {
            Map<String, Object> data = objectMapper.readValue(event, Map.class);
            String eventType = (String) data.get("event");
            if (eventType == null) {
                log.warn("Event missing 'event' field: {}", event);
                return;
            }

            switch (eventType) {

                case "KYC_APPROVED":
                case "KYC_REJECTED": {
                    Long kycUserId = Long.valueOf(data.get("userId").toString());
                    String email = userClient.getProfile(kycUserId).getEmail();
                    String subject = "KYC_APPROVED".equals(eventType) ? "KYC Approved ✅" : "KYC Rejected ❌";
                    String message = "KYC_APPROVED".equals(eventType)
                            ? "Your KYC has been successfully approved. You can now use all features."
                            : "Your KYC has been rejected. Reason: " + data.get("reason");
                    emailService.sendHtml(email, subject,
                            buildEmailHtml(subject, message, "-", "-", "KYC-" + kycUserId));
                    log.info("KYC email sent to {}", email);
                    break;
                }

                case "TRANSFER_SUCCESS": {
                    Long senderId   = Long.valueOf(data.get("senderId").toString());
                    Long receiverId = Long.valueOf(data.get("receiverId").toString());
                    String senderEmail   = userClient.getProfile(senderId).getEmail();
                    String receiverEmail = userClient.getProfile(receiverId).getEmail();
                    String amount    = String.valueOf(data.get("amount"));
                    String balance   = String.valueOf(data.get("balance"));
                    String reference = String.valueOf(data.getOrDefault("reference", "N/A"));
                    emailService.sendHtml(senderEmail, "Transfer Successful",
                            buildEmailHtml("Money Sent", "You have successfully sent money.", amount, balance, reference));
                    emailService.sendHtml(receiverEmail, "Money Received",
                            buildEmailHtml("Money Received", "You have received money.", amount, balance, reference));
                    break;
                }

                case "TOPUP_SUCCESS":
                case "WITHDRAW_SUCCESS":
                case "PAYMENT_SUCCESS":
                case "POINTS_EARNED":
                case "REDEEM_SUCCESS": {
                    Long userId   = Long.valueOf(data.get("userId").toString());
                    String userEmail = userClient.getProfile(userId).getEmail();
                    String amt = String.valueOf(data.getOrDefault("amount", "0"));
                    String bal = String.valueOf(data.getOrDefault("balance", "0"));
                    String ref = String.valueOf(data.getOrDefault("reference", "N/A"));
                    String subjectOther = getSubject(eventType);
                    String messageOther = getMessage(eventType);
                    emailService.sendHtml(userEmail, subjectOther,
                            buildEmailHtml(subjectOther, messageOther, amt, bal, ref));
                    break;
                }

                default:
                    log.debug("Unhandled event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing Kafka event: {}", event, e);
        }
    }

    private String getSubject(String eventType) {
        return switch (eventType) {
            case "TOPUP_SUCCESS"    -> "Wallet Top-up Confirmation";
            case "WITHDRAW_SUCCESS" -> "Withdrawal Confirmation";
            case "PAYMENT_SUCCESS"  -> "Payment Confirmation";
            case "POINTS_EARNED"    -> "Reward Points Earned";
            case "REDEEM_SUCCESS"   -> "Points Redemption Successful";
            default -> "Notification";
        };
    }

    private String getMessage(String eventType) {
        return switch (eventType) {
            case "TOPUP_SUCCESS"    -> "Your wallet has been successfully credited.";
            case "WITHDRAW_SUCCESS" -> "Amount has been debited from your wallet.";
            case "PAYMENT_SUCCESS"  -> "Your payment has been processed successfully.";
            case "POINTS_EARNED"    -> "You have earned reward points.";
            case "REDEEM_SUCCESS"   -> "Your reward points have been redeemed.";
            default -> "Transaction update.";
        };
    }

    private String buildEmailHtml(String title, String message,
                                  String amount, String balance, String reference) {
        return "<!DOCTYPE html>" +
                "<html><body style='font-family:Arial;background:#f4f6f8;padding:20px;'>" +
                "<div style='max-width:600px;margin:auto;background:white;border-radius:10px;overflow:hidden;'>" +
                "<div style='background:#2c3e50;color:white;padding:20px;text-align:center;'>" +
                "<h2>Loyalty Wallet</h2></div>" +
                "<div style='padding:25px;'>" +
                "<h3>" + title + "</h3>" +
                "<p>" + message + "</p>" +
                "<p><b>Amount:</b> ₹" + amount + "</p>" +
                "<p><b>Reference:</b> " + reference + "</p>" +
                "<p><b>Balance:</b> ₹" + balance + "</p>" +
                "</div></div></body></html>";
    }
}
