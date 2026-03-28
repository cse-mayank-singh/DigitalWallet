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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock private RewardRepository rewardRepo;
    @Mock private RewardTransactionRepository txnRepo;
    @Mock private RewardItemRepository itemRepo;
    @Mock private RedemptionRepository redemptionRepo;
    @Mock private WalletClient walletClient;
    @Mock private KafkaProducerService kafkaProducer;

    @InjectMocks
    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rewardService, "pointsPerRupee", 100);
        ReflectionTestUtils.setField(rewardService, "minRedeemPoints", 100);
        ReflectionTestUtils.setField(rewardService, "maxDailyRedeemPoints", 5000);
        ReflectionTestUtils.setField(rewardService, "goldThreshold", 1000);
        ReflectionTestUtils.setField(rewardService, "platinumThreshold", 5000);
        ReflectionTestUtils.setField(rewardService, "firstTopupBonus", 100);
    }

    @Test
    void earnPointsCreatesAccountAwardsBonusAndPublishesEvent() {
        RewardAccount created = RewardAccount.builder().userId(1L).points(0).tier(RewardAccount.Tier.SILVER).firstTopupDone(false).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(rewardRepo.save(any(RewardAccount.class))).thenReturn(created);

        rewardService.earnPoints(1L, new BigDecimal("1000"));

        verify(txnRepo, times(2)).save(any(RewardTransaction.class));
        verify(kafkaProducer).send(eq("reward-events"), any(Map.class));
        verify(rewardRepo, times(2)).save(any(RewardAccount.class));
    }

    @Test
    void earnPointsSkipsEarnTransactionWhenAmountTooSmallButStillGivesFirstBonus() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(0).tier(RewardAccount.Tier.SILVER).firstTopupDone(false).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));

        rewardService.earnPoints(1L, new BigDecimal("50"));

        ArgumentCaptor<RewardTransaction> captor = ArgumentCaptor.forClass(RewardTransaction.class);
        verify(txnRepo).save(captor.capture());
        assertEquals(RewardTransaction.TxnType.BONUS, captor.getValue().getType());
        assertEquals(100, account.getPoints());
    }

    @Test
    void earnPointsUpdatesTierToGold() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(950).tier(RewardAccount.Tier.SILVER).firstTopupDone(true).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));

        rewardService.earnPoints(1L, new BigDecimal("10000"));

        assertEquals(RewardAccount.Tier.GOLD, account.getTier());
    }

    @Test
    void addCatalogItemSavesMappedItem() {
        RewardItemRequest request = new RewardItemRequest(
                "Coupon",
                "Desc",
                500,
                "COUPON",
                10,
                "GOLD",
                BigDecimal.ZERO
        );
        when(itemRepo.save(any(RewardItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RewardItem item = rewardService.addCatalogItem(request);

        assertEquals("Coupon", item.getName());
        assertEquals(RewardItem.ItemType.COUPON, item.getType());
        assertEquals(true, item.getActive());
    }

    @Test
    void redeemPointsThrowsWhenBelowMinimum() {
        RewardException exception = assertThrows(RewardException.class, () -> rewardService.redeemPoints(1L, 50));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void redeemPointsThrowsWhenDailyLimitExceeded() {
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(4900);

        RewardException exception = assertThrows(RewardException.class, () -> rewardService.redeemPoints(1L, 200));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void redeemPointsThrowsWhenAccountMissing() {
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.empty());

        RewardException exception = assertThrows(RewardException.class, () -> rewardService.redeemPoints(1L, 200));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void redeemPointsThrowsWhenInsufficientPoints() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(150).tier(RewardAccount.Tier.SILVER).build();
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));

        RewardException exception = assertThrows(RewardException.class, () -> rewardService.redeemPoints(1L, 200));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void redeemPointsCreditsWalletAndSavesTransaction() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(500).tier(RewardAccount.Tier.SILVER).build();
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(walletClient.credit(1L, new BigDecimal("2"))).thenReturn(ResponseEntity.ok().build());

        rewardService.redeemPoints(1L, 200);

        assertEquals(300, account.getPoints());
        verify(walletClient).credit(1L, new BigDecimal("2"));
        verify(txnRepo).save(any(RewardTransaction.class));
    }

    @Test
    void convertPointsToCashDelegatesToRedeemLogic() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(500).tier(RewardAccount.Tier.SILVER).build();
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(walletClient.credit(1L, new BigDecimal("2"))).thenReturn(ResponseEntity.ok().build());

        rewardService.convertPointsToCash(1L, 200);

        verify(walletClient).credit(1L, new BigDecimal("2"));
    }

    @Test
    void redeemRewardThrowsWhenItemMissing() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.empty());

        RewardException exception = assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void redeemRewardThrowsWhenItemInactive() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).active(false).stock(1).pointsRequired(100).type(RewardItem.ItemType.COUPON).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));

        assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));
    }

    @Test
    void redeemRewardThrowsWhenOutOfStock() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).active(true).stock(0).pointsRequired(100).type(RewardItem.ItemType.COUPON).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));

        assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));
    }

    @Test
    void redeemRewardThrowsWhenTierIneligible() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.SILVER).build();
        RewardItem item = RewardItem.builder().id(1L).active(true).stock(1).pointsRequired(100).type(RewardItem.ItemType.COUPON).tierRequired("GOLD").build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));

        assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));
    }

    @Test
    void redeemRewardThrowsWhenInsufficientPoints() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(50).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).active(true).stock(1).pointsRequired(100).type(RewardItem.ItemType.COUPON).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));

        assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));
    }

    @Test
    void redeemRewardThrowsWhenDailyLimitExceeded() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).active(true).stock(1).pointsRequired(200).type(RewardItem.ItemType.COUPON).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(4900);

        assertThrows(RewardException.class, () -> rewardService.redeemReward(1L, 1L));
    }

    @Test
    void redeemRewardCouponGeneratesCouponCodeAndReducesStock() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).name("Coupon").active(true).stock(2).pointsRequired(200).type(RewardItem.ItemType.COUPON).build();
        Redemption saved = Redemption.builder().id(1L).userId(1L).rewardId(1L).pointsUsed(200).status(Redemption.RedemptionStatus.COMPLETED).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(redemptionRepo.save(any(Redemption.class))).thenReturn(saved);

        Redemption redemption = rewardService.redeemReward(1L, 1L);

        assertEquals(800, account.getPoints());
        assertEquals(1, item.getStock());
        verify(redemptionRepo).save(any(Redemption.class));
        assertEquals(1L, redemption.getId());
    }

    @Test
    void redeemRewardCashbackCreditsWallet() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(1000).tier(RewardAccount.Tier.GOLD).build();
        RewardItem item = RewardItem.builder().id(1L).name("Cashback").active(true).stock(2).pointsRequired(200).type(RewardItem.ItemType.CASHBACK).cashbackAmount(new BigDecimal("25")).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));
        when(itemRepo.findById(1L)).thenReturn(Optional.of(item));
        when(txnRepo.sumRedeemedPointsToday(eq(1L), eq(RewardTransaction.TxnType.REDEEM), any(), any())).thenReturn(0);
        when(walletClient.credit(1L, new BigDecimal("25"))).thenReturn(ResponseEntity.ok().build());
        when(redemptionRepo.save(any(Redemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rewardService.redeemReward(1L, 1L);

        verify(walletClient).credit(1L, new BigDecimal("25"));
    }

    @Test
    void getSummaryReturnsNextTierProgress() {
        RewardAccount account = RewardAccount.builder().userId(1L).points(800).tier(RewardAccount.Tier.SILVER).build();
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(account));

        RewardSummaryDto summary = rewardService.getSummary(1L);

        assertEquals("GOLD", summary.getNextTier());
        assertEquals(200, summary.getPointsToNextTier());
    }

    @Test
    void getCatalogDelegatesToRepository() {
        when(itemRepo.findByActiveTrueOrderByPointsRequiredAsc()).thenReturn(List.of(RewardItem.builder().id(1L).build()));

        assertEquals(1, rewardService.getCatalog().size());
    }

    @Test
    void getTransactionsDelegatesToRepository() {
        when(txnRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(RewardTransaction.builder().id(1L).build()));

        assertEquals(1, rewardService.getTransactions(1L).size());
    }

    @Test
    void createAccountIfNotExistsCreatesWhenMissing() {
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.empty());

        rewardService.createAccountIfNotExists(1L);

        verify(rewardRepo).save(any(RewardAccount.class));
    }

    @Test
    void createAccountIfNotExistsSkipsWhenPresent() {
        when(rewardRepo.findByUserId(1L)).thenReturn(Optional.of(RewardAccount.builder().userId(1L).build()));

        rewardService.createAccountIfNotExists(1L);

        verify(rewardRepo, never()).save(any(RewardAccount.class));
    }
}
