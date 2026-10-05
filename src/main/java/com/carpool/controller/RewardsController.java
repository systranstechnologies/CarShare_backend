package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.rewards.CreateRedemptionRequest;
import com.carpool.entity.*;
import com.carpool.security.AuthFacade;
import com.carpool.service.RedemptionService;
import com.carpool.service.ReferralService;
import com.carpool.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardsController {
    private final AuthFacade authFacade;
    private final WalletService walletService;
    private final ReferralService referralService;
    private final RedemptionService redemptionService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        var principal = authFacade.currentUser();
        UserWallet wallet = walletService.getWallet(principal.getUserId());
        ReferralProfile profile = referralService.profileFor(principal.getUserId());
        RewardSettings settings = referralService.settings();
        List<Referral> referrals = referralService.referralsFor(principal.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("availableCoins", wallet.getAvailableCoins());
        data.put("pendingCoins", wallet.getPendingCoins());
        data.put("redemptionPendingCoins", wallet.getRedemptionPendingCoins());
        data.put("totalEarnedCoins", wallet.getTotalEarnedCoins());
        data.put("usedCoins", wallet.getUsedCoins());
        data.put("redeemedCoins", wallet.getRedeemedCoins());
        data.put("expiredCoins", wallet.getExpiredCoins());
        data.put("referralCode", profile.getReferralCode());
        data.put("referralLink", settings.getReferralBaseUrl() + "?ref=" + profile.getReferralCode());
        data.put("totalReferrals", referrals.size());
        data.put("pendingReferrals", count(referrals, "PENDING"));
        data.put("qualifiedReferrals", count(referrals, "QUALIFIED"));
        data.put("rewardedReferrals", count(referrals, "REWARDED"));
        data.put("referrals", referrals.stream().map(this::referralView).toList());
        data.put("transactions", walletService.history(principal.getUserId()).stream().map(this::transactionView).toList());
        data.put("redemptions", redemptionService.mine().stream().map(this::redemptionView).toList());
        data.put("minimumRedemption", settings.getMinimumRedemption());
        data.put("maximumRedemption", settings.getMaximumRedemption());
        data.put("subscriptionCoinPercentage", settings.getSubscriptionCoinPercentage());
        return ApiResponse.of(data);
    }

    @PostMapping("/redemptions")
    public ApiResponse<Map<String, Object>> redeem(@Valid @RequestBody CreateRedemptionRequest request) {
        return ApiResponse.of(redemptionView(redemptionService.submit(request)));
    }

    private long count(List<Referral> referrals, String status) { return referrals.stream().filter(r -> status.equals(r.getStatus())).count(); }
    private Map<String, Object> referralView(Referral r) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", r.getId()); value.put("name", r.getReferredUser().getFullName()); value.put("userType", r.getReferredRole());
        value.put("joinDate", r.getRegisteredAt()); value.put("status", r.getStatus()); value.put("coinsEarned", r.getCoinsAwarded());
        return value;
    }
    private Map<String, Object> transactionView(WalletTransaction t) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", t.getId()); value.put("date", t.getCreatedAt()); value.put("type", t.getTransactionType());
        value.put("coinsAdded", t.getCoinsAdded()); value.put("coinsDeducted", t.getCoinsDeducted()); value.put("balanceAfter", t.getBalanceAfter()); value.put("remarks", t.getRemarks());
        return value;
    }
    private Map<String, Object> redemptionView(RedemptionRequest r) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", r.getId()); value.put("bankName", r.getBankName()); value.put("accountNumber", "••••" + r.getAccountNumberLast4());
        value.put("amount", r.getAmount()); value.put("status", r.getStatus()); value.put("requestDate", r.getCreatedAt()); value.put("rejectionReason", r.getRejectionReason()); value.put("paymentReference", r.getPaymentReference());
        return value;
    }
}
