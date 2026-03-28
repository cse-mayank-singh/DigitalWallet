package com.loyaltyService.reward_service.repository;

import com.loyaltyService.reward_service.entity.RewardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface RewardItemRepository extends JpaRepository<RewardItem, Long> {
    List<RewardItem> findByActiveTrueOrderByPointsRequiredAsc();
}
