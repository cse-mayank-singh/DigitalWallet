package com.loyaltyService.reward_service.service;

import com.loyaltyService.reward_service.dto.RewardSummaryDto;
import com.loyaltyService.reward_service.entity.RewardItem;
import com.loyaltyService.reward_service.entity.RewardTransaction;

import java.util.List;

/**
 * CQRS — Query side: all read operations for Reward.
 * Results are cached in Redis.
 */
public interface RewardQueryService {

    RewardSummaryDto getSummary(Long userId);

    List<RewardItem> getCatalog();

    List<RewardTransaction> getTransactions(Long userId);
}
