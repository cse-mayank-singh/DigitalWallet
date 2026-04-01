package com.loyaltyService.reward_service.controller;

import com.loyaltyService.reward_service.dto.ApiResponse;
import com.loyaltyService.reward_service.dto.RedeemRequest;
import com.loyaltyService.reward_service.dto.RewardItemRequest;
import com.loyaltyService.reward_service.dto.RewardSummaryDto;
import com.loyaltyService.reward_service.entity.Redemption;
import com.loyaltyService.reward_service.entity.RewardItem;
import com.loyaltyService.reward_service.entity.RewardTransaction;
import com.loyaltyService.reward_service.service.RewardCommandService;
import com.loyaltyService.reward_service.service.RewardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * CQRS Controller — GET endpoints use RewardQueryService (Redis cached),
 * write endpoints use RewardCommandService (cache-evicting).
 */
@Validated
@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
@Tag(name = "Rewards", description = "Points, tiers, catalog, redemption")
@SecurityRequirement(name = "bearerAuth")
public class RewardController {

    // CQRS: Query side (cached reads)
    private final RewardQueryService rewardQueryService;
    // CQRS: Command side (writes + cache eviction)
    private final RewardCommandService rewardCommandService;

    // ── Summary ───────────────────────────────────────────────────────────────
    @GetMapping("/summary")
    @Operation(summary = "Get reward summary (points, tier, next tier)")
    public ResponseEntity<ApiResponse<RewardSummaryDto>> summary(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Summary fetched", rewardQueryService.getSummary(userId)));
    }

    // ── Catalog ───────────────────────────────────────────────────────────────
    @GetMapping("/catalog")
    @Operation(summary = "Get reward catalog")
    public ResponseEntity<ApiResponse<List<RewardItem>>> catalog() {
        return ResponseEntity.ok(ApiResponse.ok("Catalog fetched", rewardQueryService.getCatalog()));
    }

    // ── Redeem catalog item ───────────────────────────────────────────────────
    @PostMapping("/redeem")
    @Operation(summary = "Redeem a catalog reward item (CASHBACK items credit wallet automatically)")
    public ResponseEntity<ApiResponse<Redemption>> redeem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody RedeemRequest req) {
        Redemption r = rewardCommandService.redeemReward(userId, req.getRewardId());
        return ResponseEntity.ok(ApiResponse.ok("Redemption successful", r));
    }

    // ── Redeem points → wallet cash ───────────────────────────────────────────
    @PostMapping("/redeem-points")
    @Operation(summary = "Redeem points as wallet cash (1 point = ₹1). Daily cap applies.")
    public ResponseEntity<ApiResponse<Void>> redeemPoints(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam @Min(value = 1, message = "Points must be at least 1") Integer points) {
        rewardCommandService.redeemPoints(userId, points);
        return ResponseEntity.ok(ApiResponse.ok(
                "Successfully redeemed " + points + " points. ₹" + points + " credited to your wallet."));
    }

    // ── Convert points → cash (backward compat) ───────────────────────────────
    @PostMapping("/convert-to-cash")
    @Operation(summary = "Convert points to wallet cash — alias for /redeem-points")
    public ResponseEntity<ApiResponse<Void>> convertToCash(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam @Min(value = 1, message = "Points must be at least 1") Integer points) {
        rewardCommandService.convertPointsToCash(userId, points);
        return ResponseEntity.ok(ApiResponse.ok(
                "Points converted successfully. ₹" + points + " credited to wallet."));
    }

    // ── Transaction history ───────────────────────────────────────────────────
    @GetMapping("/transactions")
    @Operation(summary = "Get reward transaction history")
    public ResponseEntity<ApiResponse<List<RewardTransaction>>> transactions(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Transactions fetched", rewardQueryService.getTransactions(userId)));
    }

    // ── Admin — add catalog item ──────────────────────────────────────────────
    @PostMapping("/catalog/add")
    @Operation(summary = "Admin — add reward item to catalog")
    public ResponseEntity<ApiResponse<RewardItem>> addCatalogItem(
            @RequestHeader("X-User-Role") String role,
            @RequestBody RewardItemRequest req) {
        if (!"ADMIN".equals(role))
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<RewardItem>builder()
                            .success(false)
                            .message("Access denied — ADMIN role required")
                            .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reward item added", rewardCommandService.addCatalogItem(req)));
    }

    // ── Internal endpoints (service-to-service) ───────────────────────────────
    @PostMapping("/internal/create-account")
    @Operation(summary = "Internal — create reward account for new user")
    public ResponseEntity<Void> createAccount(@RequestParam Long userId) {
        rewardCommandService.createAccountIfNotExists(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/internal/earn")
    @Operation(summary = "Internal — earn points from topup (called by wallet-service)")
    public ResponseEntity<Void> earnInternal(
            @RequestParam Long userId,
            @RequestParam java.math.BigDecimal amount) {
        rewardCommandService.earnPoints(userId, amount);
        return ResponseEntity.ok().build();
    }
}
