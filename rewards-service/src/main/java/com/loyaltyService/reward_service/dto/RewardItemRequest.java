package com.loyaltyService.reward_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardItemRequest {
    private String name;
    private String description;
    private Integer pointsRequired;
    private String type;           // must match RewardItem.ItemType enum: CASHBACK | COUPON | VOUCHER
    private Integer stock;
    private String tierRequired;   // SILVER | GOLD | PLATINUM
    private BigDecimal cashbackAmount;
}
