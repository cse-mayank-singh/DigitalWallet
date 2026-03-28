package com.loyaltyService.auth_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String recipientEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("shivamkumar1352002@gmail.com");
        message.setTo(recipientEmail);             
        message.setSubject("Your OTP Code");
        message.setText("Your OTP is: " + otp);
        mailSender.send(message);
    }
}