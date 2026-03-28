package com.loyaltyService.reward_service.service;

import com.loyaltyService.reward_service.client.WalletClient;
import com.loyaltyService.reward_service.dto.RewardItemRequest;
import com.loyaltyService.reward_service.dto.RewardSummaryDto;
import com.loyaltyService.reward_service.entity.Redemption;
import com.loyaltyService.reward_service.entity.RewardAccount;
import com.loyaltyService.reward_service.entity.RewardItem;
import com.loyaltyService.reward_service.entity.RewardTransaction;
import com.loyaltyService.reward_service.exception.RewardException;
import com.loyaltyService.reward_service.repository.RedemptionRepository;
import com.loyaltyService.reward_service.repository.RewardItemRepository;
import com.loyaltyService.reward_service.repository.RewardRepository;
import com.loyaltyService.reward_service.repository.RewardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardService {

    private final RewardRepository rewardRepo;
    private final RewardTransactionRepository txnRepo;
    private final RewardItemRepository itemRepo;
    private final RedemptionRepository redemptionRepo;
    private final WalletClient walletClient;
    private final KafkaProducerService kafkaProducer;
    @Value("${rewards.points-per-rupee:100}")       private int pointsPerRupee;
    @Value("${rewards.min-redeem-points:100}")       private int minRedeemPoints;
    @Value("${rewards.max-daily-redeem-points:5000}") private int maxDailyRedeemPoints;  // FIX: new limit
    @Value("${rewards.tiers.gold-threshold:1000}")   private int goldThreshold;
    @Value("${rewards.tiers.platinum-threshold:5000}") private int platinumThreshold;
    @Value("${rewards.bonus.first-topup-points:100}") private int firstTopupBonus;

    // ── EARN POINTS ───────────────────────────────────────────────────────────
    @Transactional
    public void earnPoints(Long userId, BigDecimal amount) {
        RewardAccount acc = rewardRepo.findByUserId(userId)
                .orElseGet(() -> rewardRepo.save(RewardAccount.builder().userId(userId).build()));

        int earned = amount.intValue() / pointsPerRupee;
        if (earned > 0) {
            acc.setPoints(acc.getPoints() + earned);
            txnRepo.save(RewardTransaction.builder()
                    .userId(userId)
                    .points(earned)
                    .type(RewardTransaction.TxnType.EARN)
                    .description("Points earned on ₹" + amount + " topup")
                    .expiryDate(LocalDateTime.now().plusDays(365))
                    .build());
        }

        // First topup bonus
        if (Boolean.FALSE.equals(acc.getFirstTopupDone())) {
            acc.setPoints(acc.getPoints() + firstTopupBonus);
            acc.setFirstTopupDone(true);
            txnRepo.save(RewardTransaction.builder()
                    .userId(userId)
                    .points(firstTopupBonus)
                    .type(RewardTransaction.TxnType.BONUS)
                    .description("Welcome bonus — first top-up!")
                    .expiryDate(LocalDateTime.now().plusDays(365))
                    .build());
        }

        updateTier(acc);
        rewardRepo.save(acc);
        log.info("Points earned: userId={}, earned={}, total={}", userId, earned, acc.getPoints());
        kafkaProducer.send("reward-events", Map.of(
                "event", "POINTS_EARNED",
                "userId", userId,
                "amount", earned,
                "balance", acc.getPoints()
        ));
    }

    // ── ADD CATALOG ITEM ──────────────────────────────────────────────────────
    @Transactional
    public RewardItem addCatalogItem(RewardItemRequest req) {
        return itemRepo.save(RewardItem.builder()
                .name(req.getName())
                .description(req.getDescription())
                .pointsRequired(req.getPointsRequired())
                .type(RewardItem.ItemType.valueOf(req.getType()))
                .active(true)
                .stock(req.getStock())
                .tierRequired(req.getTierRequired())
                .cashbackAmount(req.getCashbackAmount())
                .build());
    }

    // ── REDEEM (simple points → wallet cash) ──────────────────────────────────
    // FIX: Added daily redemption limit. Previously redeemPoints() burned points
    //      but did NOT credit the wallet — it was essentially a no-op for the user.
    //      Now it converts points to cash and credits the wallet (same as convertPointsToCash).
    @Transactional
    public void redeemPoints(Long userId, Integer points) {
        if (points < minRedeemPoints)
            throw new RewardException("Minimum " + minRedeemPoints + " points required for redemption");

        // FIX: daily redemption limit check
        int redeemedToday = getTodayRedeemedPoints(userId);
        if (redeemedToday + points > maxDailyRedeemPoints)
            throw new RewardException(
                    "Daily redemption limit of " + maxDailyRedeemPoints + " points exceeded. " +
                            "Already redeemed today: " + redeemedToday + " points."
            );

        RewardAccount acc = findAccount(userId);
        if (acc.getPoints() < points)
            throw new RewardException("Insufficient points. Available: " + acc.getPoints());

        // 1 point = ₹1 conversion
        BigDecimal cashAmount = BigDecimal.valueOf(points/100);

        acc.setPoints(acc.getPoints() - points);
        updateTier(acc);
        rewardRepo.save(acc);

        // FIX: Actually credit the wallet — this was MISSING before
        try {
            walletClient.credit(userId, cashAmount);
        } catch (Exception e) {
            // Rethrow so the transaction rolls back; the fallback logs but doesn't re-throw
            throw new RewardException("Wallet service unavailable. Please try again later.");
        }

        txnRepo.save(RewardTransaction.builder()
                .userId(userId)
                .points(points)
                .type(RewardTransaction.TxnType.REDEEM)
                .description("Redeemed " + points + " points → ₹" + cashAmount + " credited to wallet")
                .build());

        log.info("Points redeemed: userId={}, points={}, cash=₹{}", userId, points, cashAmount);
    }

    // ── CONVERT POINTS TO CASH (same as redeemPoints — kept for backward compatibility) ──
    @Transactional
    public void convertPointsToCash(Long userId, Integer points) {
        // Delegate so both endpoints share the same limit/validation logic
        redeemPoints(userId, points);
    }

    // ── REDEEM (catalog item) ─────────────────────────────────────────────────
    @Transactional
    public Redemption redeemReward(Long userId, Long rewardId) {
        RewardAccount acc = findAccount(userId);
        RewardItem item = itemRepo.findById(rewardId)
                .orElseThrow(() -> new RewardException("Reward item not found", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(item.getActive()))
            throw new RewardException("This reward is no longer available");
        if (item.getStock() <= 0)
            throw new RewardException("This reward is out of stock");
        if (item.getTierRequired() != null && !isTierEligible(acc.getTier(), item.getTierRequired()))
            throw new RewardException("Your tier (" + acc.getTier() + ") is not eligible. Required: " + item.getTierRequired());
        if (acc.getPoints() < item.getPointsRequired())
            throw new RewardException("Insufficient points. Need " + item.getPointsRequired() + ", have " + acc.getPoints());

        // FIX: daily redemption limit applies to catalog items too
        int redeemedToday = getTodayRedeemedPoints(userId);
        if (redeemedToday + item.getPointsRequired() > maxDailyRedeemPoints)
            throw new RewardException(
                    "Daily redemption limit of " + maxDailyRedeemPoints + " points exceeded. " +
                            "Already redeemed today: " + redeemedToday + " points."
            );

        acc.setPoints(acc.getPoints() - item.getPointsRequired());
        item.setStock(item.getStock() - 1);

        Redemption r = Redemption.builder()
                .userId(userId)
                .rewardId(rewardId)
                .pointsUsed(item.getPointsRequired())
                .status(Redemption.RedemptionStatus.COMPLETED)
                .build();

        if (item.getType() == RewardItem.ItemType.COUPON)
            r.setCouponCode("CPN" + System.currentTimeMillis());

        if (item.getType() == RewardItem.ItemType.CASHBACK && item.getCashbackAmount() != null) {
            try {
                walletClient.credit(userId, item.getCashbackAmount());
            } catch (Exception e) {
                throw new RewardException("Wallet service unavailable. Please try again later.");
            }
        }

        txnRepo.save(RewardTransaction.builder()
                .userId(userId)
                .points(item.getPointsRequired())
                .type(RewardTransaction.TxnType.REDEEM)
                .description("Redeemed: " + item.getName())
                .build());

        rewardRepo.save(acc);
        itemRepo.save(item);
        updateTier(acc);
        return redemptionRepo.save(r);
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────
    public RewardSummaryDto getSummary(Long userId) {
        RewardAccount acc = findAccount(userId);
        String nextTier;
        int needed;
        if (acc.getPoints() < goldThreshold) {
            nextTier = "GOLD";
            needed = goldThreshold - acc.getPoints();
        } else if (acc.getPoints() < platinumThreshold) {
            nextTier = "PLATINUM";
            needed = platinumThreshold - acc.getPoints();
        } else {
            nextTier = "PLATINUM (MAX)";
            needed = 0;
        }
        return RewardSummaryDto.builder()
                .userId(userId)
                .points(acc.getPoints())
                .tier(acc.getTier().name())
                .nextTier(nextTier)
                .pointsToNextTier(needed)
                .build();
    }

    public List<RewardItem> getCatalog() {
        return itemRepo.findByActiveTrueOrderByPointsRequiredAsc();
    }

    public List<RewardTransaction> getTransactions(Long userId) {
        return txnRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ── CREATE ACCOUNT ────────────────────────────────────────────────────────
    @Transactional
    public void createAccountIfNotExists(Long userId) {
        if (rewardRepo.findByUserId(userId).isPresent()) {
            log.info("Reward account already exists for userId={}", userId);
            return;
        }
        rewardRepo.save(RewardAccount.builder()
                .userId(userId)
                .points(0)
                .tier(RewardAccount.Tier.SILVER)
                .firstTopupDone(false)
                .build());
        log.info("Reward account created for userId={}", userId);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private void updateTier(RewardAccount acc) {
        if (acc.getPoints() >= platinumThreshold)      acc.setTier(RewardAccount.Tier.PLATINUM);
        else if (acc.getPoints() >= goldThreshold)     acc.setTier(RewardAccount.Tier.GOLD);
        else                                            acc.setTier(RewardAccount.Tier.SILVER);
    }

    private RewardAccount findAccount(Long userId) {
        return rewardRepo.findByUserId(userId)
                .orElseThrow(() -> new RewardException("Reward account not found", HttpStatus.NOT_FOUND));
    }

    private boolean isTierEligible(RewardAccount.Tier userTier, String required) {
        List<RewardAccount.Tier> tiers = List.of(
                RewardAccount.Tier.SILVER,
                RewardAccount.Tier.GOLD,
                RewardAccount.Tier.PLATINUM
        );
        return tiers.indexOf(userTier) >= tiers.indexOf(RewardAccount.Tier.valueOf(required));
    }

    /**
     * FIX: Returns total points redeemed by this user since midnight today (UTC).
     * Used to enforce the daily redemption cap.
     */
    private int getTodayRedeemedPoints(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = LocalDate.now().atTime(23, 59, 59);
        return txnRepo.sumRedeemedPointsToday(userId,
                RewardTransaction.TxnType.REDEEM, startOfDay, endOfDay);
    }
}
