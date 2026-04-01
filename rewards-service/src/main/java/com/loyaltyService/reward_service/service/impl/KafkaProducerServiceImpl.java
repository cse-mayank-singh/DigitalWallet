package com.loyaltyService.reward_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.loyaltyService.reward_service.service.KafkaProducerService;

@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void send(String topic, Object message) {
        kafkaTemplate.send(topic, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        System.out.println("✅ Message sent to topic: " + topic);
                    } else {
                        System.out.println("❌ Failed to send message: " + ex.getMessage());
                    }
                });
    }
}
