package com.loyaltyService.reward_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyaltyService.reward_service.dto.RedeemRequest;
import com.loyaltyService.reward_service.dto.RewardSummaryDto;
import com.loyaltyService.reward_service.service.RewardCommandService;
import com.loyaltyService.reward_service.service.RewardQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RewardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RewardQueryService rewardQueryService;

    @Mock
    private RewardCommandService rewardCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RewardController rewardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(rewardController).build();
    }

    @Test
    void testGetSummary() throws Exception {
        RewardSummaryDto summary = RewardSummaryDto.builder()
                .userId(1L)
                .points(500)
                .tier("SILVER")
                .nextTier("GOLD")
                .pointsToNextTier(500)
                .build();

        when(rewardQueryService.getSummary(1L)).thenReturn(summary);

        mockMvc.perform(get("/api/rewards/summary")
                .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(500))
                .andExpect(jsonPath("$.data.tier").value("SILVER"));
    }

    @Test
    void testRedeemPoints() throws Exception {
        mockMvc.perform(post("/api/rewards/redeem-points")
                .header("X-User-Id", "1")
                .queryParam("points", "200")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(rewardCommandService, times(1)).redeemPoints(1L, 200);
    }
}
