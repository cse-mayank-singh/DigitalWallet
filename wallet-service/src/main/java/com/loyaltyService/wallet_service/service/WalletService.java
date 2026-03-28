package com.loyaltyService.wallet_service.service;

import com.loyaltyService.wallet_service.client.RewardClient;
import com.loyaltyService.wallet_service.dto.WalletBalanceResponse;
import com.loyaltyService.wallet_service.entity.LedgerEntry;
import com.loyaltyService.wallet_service.entity.Transaction;
import com.loyaltyService.wallet_service.entity.WalletAccount;
import com.loyaltyService.wallet_service.exception.WalletException;
import com.loyaltyService.wallet_service.repository.TransactionRepository;
import com.loyaltyService.wallet_service.repository.WalletAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j @Service @RequiredArgsConstructor
public class WalletService {

    private final WalletAccountRepository accountRepo;
    private final TransactionRepository txnRepo;
    private final LedgerService ledgerService;
    private final RewardClient rewardClient;
    private final KafkaProducerService kafkaProducer;

    @Value("${wallet.daily-topup-limit:50000}") private BigDecimal dailyTopupLimit;
    @Value("${wallet.daily-transfer-limit:25000}") private BigDecimal dailyTransferLimit;
    @Value("${wallet.max-daily-transfers:10}") private int maxDailyTransfers;

    // ── Create ────────────────────────────────────────────────────────────────
    @Transactional
    public void createWallet(Long userId) {
        if (accountRepo.existsByUserId(userId))
            throw new WalletException("Wallet already exists for this user", HttpStatus.CONFLICT);
        accountRepo.save(WalletAccount.builder().userId(userId).build());
        log.info("Wallet created for userId={}", userId);
    }

    // ── Balance ───────────────────────────────────────────────────────────────
    public WalletBalanceResponse getBalance(Long userId) {
        WalletAccount acc = findWallet(userId);
        return WalletBalanceResponse.builder()
            .userId(userId).balance(acc.getBalance())
            .status(acc.getStatus().name()).lastUpdated(acc.getUpdatedAt()).build();
    }

    // ── Topup ─────────────────────────────────────────────────────────────────
    @Transactional
    public void topup(Long userId, BigDecimal amount, String idempotencyKey) {
        // Idempotency check
        if (idempotencyKey != null) {
            Optional<Transaction> existing = txnRepo.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate topup ignored: idempotencyKey={}", idempotencyKey);
                return;
            }
        }
        WalletAccount acc = findActiveWallet(userId);
        // Daily limit check
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = LocalDate.now().atTime(23, 59, 59);
        BigDecimal todayTotal = txnRepo.sumTodayTopups(userId,
                Transaction.TxnType.TOPUP,
                Transaction.TxnStatus.SUCCESS,
                start,
                end);
        if (todayTotal.add(amount).compareTo(dailyTopupLimit) > 0)
            throw new WalletException("Daily top-up limit of ₹" + dailyTopupLimit + " exceeded");

        String ref = "TOPUP_" + UUID.randomUUID();

        // LEDGER FIRST — then update balance
        ledgerService.record(userId, amount, LedgerEntry.EntryType.CREDIT, ref, "Wallet top-up");

        acc.credit(amount);
        accountRepo.save(acc);

        txnRepo.save(Transaction.builder()
            .receiverId(userId).amount(amount)
            .status(Transaction.TxnStatus.SUCCESS).type(Transaction.TxnType.TOPUP)
            .referenceId(ref).idempotencyKey(idempotencyKey).build());

        // Fire-and-forget to rewards service (fallback handles failure gracefully)
        rewardClient.earnPoints(userId, amount);
        log.info("Topup success: userId={}, amount={}, ref={}", userId, amount, ref);
        kafkaProducer.send("wallet-events", Map.of(
                "event", "TOPUP_SUCCESS",
                "userId", userId,
                "amount", amount,
                "reference", ref,
                "balance", acc.getBalance()   // ✅ ADD THIS
        ));
    }

    // ── Transfer ──────────────────────────────────────────────────────────────
    @Transactional
    public void transfer(Long senderId, Long receiverId, BigDecimal amount, String idempotencyKey, String description) {
        if (senderId.equals(receiverId))
            throw new WalletException("Cannot transfer to yourself");
        if (idempotencyKey != null && txnRepo.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.info("Duplicate transfer ignored: idempotencyKey={}", idempotencyKey);
            return;
        }
        WalletAccount sender   = findActiveWallet(senderId);
        WalletAccount receiver = findWallet(receiverId);

        if (sender.getBalance().compareTo(amount) < 0)
            throw new WalletException("Insufficient balance");

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = LocalDate.now().atTime(23, 59, 59);
        BigDecimal todayAmt = txnRepo.sumTodayTransfers(senderId,
                Transaction.TxnType.TRANSFER, Transaction.TxnStatus.SUCCESS, start, end);
        if (todayAmt.add(amount).compareTo(dailyTransferLimit) > 0)
            throw new WalletException("Daily transfer limit of ₹" + dailyTransferLimit + " exceeded");
        if (txnRepo.countTodayTransfers(senderId,
                Transaction.TxnType.TRANSFER, Transaction.TxnStatus.SUCCESS, start, end) >= maxDailyTransfers)
            throw new WalletException("Maximum " + maxDailyTransfers + " transfers per day reached");

        String ref = "TXN_" + UUID.randomUUID();
        ledgerService.record(senderId,   amount, LedgerEntry.EntryType.DEBIT,  ref, description);
        ledgerService.record(receiverId, amount, LedgerEntry.EntryType.CREDIT, ref, description);
        sender.debit(amount);
        receiver.credit(amount);
        accountRepo.save(sender);
        accountRepo.save(receiver);
        txnRepo.save(Transaction.builder()
                .senderId(senderId).receiverId(receiverId).amount(amount)
                .status(Transaction.TxnStatus.SUCCESS).type(Transaction.TxnType.TRANSFER)
                .referenceId(ref).idempotencyKey(idempotencyKey).description(description).build());
        rewardClient.earnPoints(senderId, amount);
        log.info("Transfer success: from={}, to={}, amount={}, ref={}", senderId, receiverId, amount, ref);
        kafkaProducer.send("wallet-events", Map.of(
                "event", "TRANSFER_SUCCESS",
                "senderId", senderId,
                "receiverId", receiverId,
                "amount", amount,
                "reference", ref,
                "balance", sender.getBalance()
        ));
    }

    // ── Withdraw ──────────────────────────────────────────────────────────────
    @Transactional
    public void withdraw(Long userId, BigDecimal amount) {
        WalletAccount acc = findActiveWallet(userId);
        if (acc.getBalance().compareTo(amount) < 0)
            throw new WalletException("Insufficient balance");
        String ref = "WITHDRAW_" + UUID.randomUUID();
        ledgerService.record(userId, amount, LedgerEntry.EntryType.DEBIT, ref, "Wallet withdrawal");
        acc.debit(amount);
        accountRepo.save(acc);
        txnRepo.save(Transaction.builder().senderId(userId).amount(amount)
                .status(Transaction.TxnStatus.SUCCESS).type(Transaction.TxnType.WITHDRAW)
                .referenceId(ref).build());
        log.info("Withdrawal success: userId={}, amount={}, ref={}", userId, amount, ref);

    }

    // ── Internal credit (cashback from rewards) ───────────────────────────────
    @Transactional
    public void creditInternal(Long userId, BigDecimal amount, String source) {
        WalletAccount acc = findWallet(userId);
        // FIX: choose correct TxnType based on source so history is readable
        Transaction.TxnType txnType = "REDEEM".equalsIgnoreCase(source)
                ? Transaction.TxnType.REDEEM
                : Transaction.TxnType.CASHBACK;
        String prefix = txnType == Transaction.TxnType.REDEEM ? "REDEEM_" : "CASHBACK_";
        String ref = prefix + UUID.randomUUID();
        String desc = txnType == Transaction.TxnType.REDEEM
                ? "Points redeemed → ₹" + amount + " credited"
                : "Cashback credit";

        ledgerService.record(userId, amount, LedgerEntry.EntryType.CREDIT, ref, desc);
        acc.credit(amount);
        accountRepo.save(acc);
        txnRepo.save(Transaction.builder().receiverId(userId).amount(amount)
                .status(Transaction.TxnStatus.SUCCESS).type(txnType)
                .referenceId(ref).description(desc).build());
        log.info("Internal credit: userId={}, amount={}, type={}", userId, amount, txnType);
    }

    // ── Status management ─────────────────────────────────────────────────────
    @Transactional
    public void creditInternal(Long userId, BigDecimal amount) {
        creditInternal(userId, amount, "CASHBACK");
    }

    // ── Status management ─────────────────────────────────────────────────────
    @Transactional
    public void updateStatus(Long userId, WalletAccount.WalletStatus status) {
        findWallet(userId);
        accountRepo.updateStatus(userId, status);
        log.info("Wallet status updated: userId={}, status={}", userId, status);
    }

    // ── Queries ───────────────────────────────────────────────────────────────
    public Page<Transaction> getTransactions(Long userId, Pageable pageable) {
        return txnRepo.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable);
    }

    public List<Transaction> getStatement(Long userId, LocalDateTime from, LocalDateTime to) {
        return txnRepo.findStatement(userId, from, to);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private WalletAccount findWallet(Long userId) {
        return accountRepo.findByUserId(userId)
                .orElseThrow(() -> new WalletException("Wallet not found for userId: " + userId, HttpStatus.NOT_FOUND));
    }

    private WalletAccount findActiveWallet(Long userId) {
        WalletAccount acc = findWallet(userId);
        if (!acc.isActive())
            throw new WalletException("Wallet is " + acc.getStatus().name().toLowerCase() + ". Contact support.", HttpStatus.FORBIDDEN);
        return acc;
    }
}
