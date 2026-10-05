package com.carpool.service;

import com.carpool.entity.User;
import com.carpool.entity.UserWallet;
import com.carpool.entity.WalletTransaction;
import com.carpool.exception.AppException;
import com.carpool.repository.UserRepository;
import com.carpool.repository.UserWalletRepository;
import com.carpool.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final UserWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserWallet ensureWallet(User user) {
        return walletRepository.findByUserId(user.getId()).orElseGet(() -> {
            UserWallet wallet = new UserWallet();
            wallet.setUser(user);
            return walletRepository.save(wallet);
        });
    }

    @Transactional
    public UserWallet credit(UUID userId, long coins, String type, String referenceType, UUID referenceId, String idempotencyKey, String remarks) {
        requirePositive(coins);
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return getWallet(userId);
        }
        UserWallet wallet = lockOrCreate(userId);
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) return wallet;
        wallet.setAvailableCoins(Math.addExact(wallet.getAvailableCoins(), coins));
        if ("REFERRAL_REWARD".equals(type) || "BONUS_COINS".equals(type)) {
            wallet.setTotalEarnedCoins(Math.addExact(wallet.getTotalEarnedCoins(), coins));
        }
        walletRepository.save(wallet);
        record(wallet, type, coins, 0, referenceType, referenceId, idempotencyKey, remarks);
        return wallet;
    }

    @Transactional
    public UserWallet debit(UUID userId, long coins, String type, String referenceType, UUID referenceId, String idempotencyKey, String remarks) {
        requirePositive(coins);
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return getWallet(userId);
        }
        UserWallet wallet = lockOrCreate(userId);
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) return wallet;
        if (wallet.getAvailableCoins() < coins) {
            throw new AppException(HttpStatus.CONFLICT, "INSUFFICIENT_COINS", "Insufficient available coins");
        }
        wallet.setAvailableCoins(wallet.getAvailableCoins() - coins);
        wallet.setUsedCoins(Math.addExact(wallet.getUsedCoins(), coins));
        walletRepository.save(wallet);
        record(wallet, type, 0, coins, referenceType, referenceId, idempotencyKey, remarks);
        return wallet;
    }

    @Transactional
    public UserWallet holdForRedemption(UUID userId, long coins, UUID redemptionId) {
        UserWallet wallet = debit(userId, coins, "COIN_REDEMPTION", "REDEMPTION", redemptionId,
            "redemption-hold:" + redemptionId, "Coins reserved for redemption");
        wallet.setUsedCoins(wallet.getUsedCoins() - coins);
        wallet.setRedemptionPendingCoins(Math.addExact(wallet.getRedemptionPendingCoins(), coins));
        return walletRepository.save(wallet);
    }

    @Transactional
    public UserWallet reserveForSubscription(UUID userId, long coins) {
        requirePositive(coins);
        UserWallet wallet = lockOrCreate(userId);
        if (wallet.getAvailableCoins() < coins) throw new AppException(HttpStatus.CONFLICT, "INSUFFICIENT_COINS", "Insufficient available coins");
        wallet.setAvailableCoins(wallet.getAvailableCoins() - coins);
        wallet.setPendingCoins(Math.addExact(wallet.getPendingCoins(), coins));
        return walletRepository.save(wallet);
    }

    @Transactional
    public UserWallet settleSubscription(UUID userId, long coins, UUID subscriptionId) {
        UserWallet wallet = lockOrCreate(userId);
        String key = "subscription-coins:" + subscriptionId;
        if (transactionRepository.existsByIdempotencyKey(key)) return wallet;
        if (wallet.getPendingCoins() < coins) throw new AppException(HttpStatus.CONFLICT, "INVALID_WALLET_STATE", "Subscription coin reservation is unavailable");
        wallet.setPendingCoins(wallet.getPendingCoins() - coins);
        wallet.setUsedCoins(Math.addExact(wallet.getUsedCoins(), coins));
        walletRepository.save(wallet);
        record(wallet, "SUBSCRIPTION_PAYMENT", 0, coins, "SUBSCRIPTION", subscriptionId, key, "Coins used toward subscription payment");
        return wallet;
    }

    @Transactional
    public UserWallet releaseSubscription(UUID userId, long coins) {
        if (coins <= 0) return getWallet(userId);
        UserWallet wallet = lockOrCreate(userId);
        if (wallet.getPendingCoins() < coins) throw new AppException(HttpStatus.CONFLICT, "INVALID_WALLET_STATE", "Subscription coin reservation is unavailable");
        wallet.setPendingCoins(wallet.getPendingCoins() - coins);
        wallet.setAvailableCoins(Math.addExact(wallet.getAvailableCoins(), coins));
        return walletRepository.save(wallet);
    }

    @Transactional
    public UserWallet releaseRedemption(UUID userId, long coins, UUID redemptionId) {
        UserWallet wallet = lockOrCreate(userId);
        if (wallet.getRedemptionPendingCoins() < coins) {
            throw new AppException(HttpStatus.CONFLICT, "INVALID_WALLET_STATE", "Redemption hold is unavailable");
        }
        String key = "redemption-refund:" + redemptionId;
        if (transactionRepository.existsByIdempotencyKey(key)) return wallet;
        wallet.setRedemptionPendingCoins(wallet.getRedemptionPendingCoins() - coins);
        wallet.setAvailableCoins(Math.addExact(wallet.getAvailableCoins(), coins));
        walletRepository.save(wallet);
        record(wallet, "REFUND", coins, 0, "REDEMPTION", redemptionId, key, "Rejected redemption returned to wallet");
        return wallet;
    }

    @Transactional
    public UserWallet settleRedemption(UUID userId, long coins, UUID redemptionId) {
        UserWallet wallet = lockOrCreate(userId);
        if (wallet.getRedemptionPendingCoins() < coins) {
            throw new AppException(HttpStatus.CONFLICT, "INVALID_WALLET_STATE", "Redemption hold is unavailable");
        }
        wallet.setRedemptionPendingCoins(wallet.getRedemptionPendingCoins() - coins);
        wallet.setRedeemedCoins(Math.addExact(wallet.getRedeemedCoins(), coins));
        return walletRepository.save(wallet);
    }

    @Transactional
    public UserWallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
            return ensureWallet(user);
        });
    }

    @Transactional
    public List<WalletTransaction> history(UUID userId) {
        return transactionRepository.findTop100ByWalletIdOrderByCreatedAtDesc(getWallet(userId).getId());
    }

    private UserWallet lockOrCreate(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
            return ensureWallet(user);
        });
    }

    private void record(UserWallet wallet, String type, long added, long deducted, String referenceType,
                        UUID referenceId, String key, String remarks) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setTransactionType(type);
        transaction.setCoinsAdded(added);
        transaction.setCoinsDeducted(deducted);
        transaction.setBalanceAfter(wallet.getAvailableCoins());
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setIdempotencyKey(key);
        transaction.setRemarks(remarks);
        transactionRepository.save(transaction);
    }

    private void requirePositive(long coins) {
        if (coins <= 0) throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_COIN_AMOUNT", "Coin amount must be positive");
    }
}
