package com.loyaltyService.wallet_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String topic, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            System.out.println("✅ Message sent: " + json);
                        } else {
                            System.out.println("❌ Failed: " + ex.getMessage());
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}