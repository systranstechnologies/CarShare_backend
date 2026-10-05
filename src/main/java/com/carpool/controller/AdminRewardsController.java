package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.rewards.UpdateRedemptionRequest;
import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.ReferralRepository;
import com.carpool.repository.RedemptionRequestRepository;
import com.carpool.repository.RewardSettingsRepository;
import com.carpool.repository.WalletTransactionRepository;
import com.carpool.security.AuthFacade;
import com.carpool.service.AuditService;
import com.carpool.service.RedemptionService;
import com.carpool.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/admin/rewards")
@RequiredArgsConstructor
public class AdminRewardsController {
    private final AuthFacade authFacade;
    private final RedemptionService redemptionService;
    private final RedemptionRequestRepository redemptionRepository;
    private final ReferralRepository referralRepository;
    private final WalletTransactionRepository transactionRepository;
    private final RewardSettingsRepository settingsRepository;
    private final WalletService walletService;
    private final AuditService auditService;

    @GetMapping("/redemptions") public ApiResponse<List<Map<String, Object>>> redemptions() {
        requireAdmin(); return ApiResponse.of(redemptionService.all().stream().map(this::adminRedemptionView).toList());
    }
    @PatchMapping("/redemptions/{id}") public ApiResponse<Map<String, Object>> transition(@PathVariable UUID id, @Valid @RequestBody UpdateRedemptionRequest request) {
        return ApiResponse.of(adminRedemptionView(redemptionService.transition(id, request)));
    }
    @GetMapping("/referrals") public ApiResponse<List<Map<String, Object>>> referrals() {
        requireAdmin(); return ApiResponse.of(referralRepository.findAll().stream().map(this::referralView).toList());
    }
    @PostMapping("/wallets/{userId}/adjust") public ApiResponse<Map<String, Object>> adjust(@PathVariable UUID userId, @RequestBody Map<String, Object> request) {
        requireAdmin();
        long amount = Long.parseLong(String.valueOf(request.getOrDefault("amount", 0)));
        String reason = String.valueOf(request.getOrDefault("reason", "Admin adjustment"));
        if (amount == 0) throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_COIN_AMOUNT", "Adjustment amount cannot be zero");
        String key = "admin-adjustment:" + UUID.randomUUID();
        UserWallet wallet = amount > 0
            ? walletService.credit(userId, amount, "ADMIN_ADJUSTMENT", "ADMIN", authFacade.currentUser().getUserId(), key, reason)
            : walletService.debit(userId, Math.abs(amount), "ADMIN_ADJUSTMENT", "ADMIN", authFacade.currentUser().getUserId(), key, reason);
        auditService.log("WALLET_ADMIN_ADJUSTMENT", authFacade.currentUser().getUserId().toString(), userId.toString(), "{\"amount\":" + amount + "}");
        return ApiResponse.of(Map.of("availableCoins", wallet.getAvailableCoins()));
    }
    @GetMapping("/settings") public ApiResponse<RewardSettings> settings() { requireAdmin(); return ApiResponse.of(settingsRepository.findById((short) 1).orElseThrow()); }
    @PutMapping("/settings") public ApiResponse<RewardSettings> updateSettings(@RequestBody RewardSettings input) {
        requireAdmin();
        if (input.getCoinsPerReferral() < 0 || input.getMinimumRedemption() < 1 || input.getMaximumRedemption() < input.getMinimumRedemption()
            || input.getSubscriptionCoinPercentage() < 0 || input.getSubscriptionCoinPercentage() > 99) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "Reward settings are invalid");
        }
        RewardSettings settings = settingsRepository.findById((short) 1).orElse(new RewardSettings());
        settings.setReferralEnabled(input.isReferralEnabled()); settings.setCoinsPerReferral(input.getCoinsPerReferral());
        settings.setMinimumRedemption(input.getMinimumRedemption()); settings.setMaximumRedemption(input.getMaximumRedemption());
        settings.setSubscriptionCoinPercentage(input.getSubscriptionCoinPercentage());
        if (input.getReferralBaseUrl() != null && !input.getReferralBaseUrl().isBlank()) settings.setReferralBaseUrl(input.getReferralBaseUrl().trim());
        settingsRepository.save(settings);
        auditService.log("REWARD_SETTINGS_UPDATED", authFacade.currentUser().getUserId().toString(), "1", "{}");
        return ApiResponse.of(settings);
    }
    @GetMapping("/metrics") public ApiResponse<Map<String, Object>> metrics() {
        requireAdmin();
        List<Referral> referrals = referralRepository.findAll(); List<RedemptionRequest> redemptions = redemptionRepository.findAll(); List<WalletTransaction> transactions = transactionRepository.findAll();
        Map<String, Long> monthlyGrowth = referrals.stream().collect(java.util.stream.Collectors.groupingBy(
            referral -> java.time.YearMonth.from(referral.getRegisteredAt().atZone(java.time.ZoneOffset.UTC)).toString(),
            java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        List<Map<String, Object>> topReferrers = referrals.stream().collect(java.util.stream.Collectors.groupingBy(Referral::getReferrer))
            .entrySet().stream().sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size())).limit(10)
            .map(entry -> Map.<String, Object>of("userId", entry.getKey().getId(), "name", entry.getKey().getFullName(),
                "totalReferrals", entry.getValue().size(), "rewardedReferrals", entry.getValue().stream().filter(r -> "REWARDED".equals(r.getStatus())).count()))
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReferrals", referrals.size()); result.put("rewardedReferrals", referrals.stream().filter(r -> "REWARDED".equals(r.getStatus())).count());
        result.put("coinsIssued", transactions.stream()
            .filter(transaction -> Set.of("REFERRAL_REWARD", "BONUS_COINS").contains(transaction.getTransactionType()))
            .mapToLong(WalletTransaction::getCoinsAdded).sum());
        result.put("coinsRedeemed", redemptions.stream().filter(r -> Set.of("PAID", "CLOSED").contains(r.getStatus())).mapToLong(RedemptionRequest::getAmount).sum());
        result.put("coinsUsedForSubscription", transactions.stream().filter(t -> "SUBSCRIPTION_PAYMENT".equals(t.getTransactionType())).mapToLong(WalletTransaction::getCoinsDeducted).sum());
        result.put("pendingRedemptions", redemptions.stream().filter(r -> Set.of("PENDING", "PROCESSING").contains(r.getStatus())).count());
        result.put("monthlyReferralGrowth", monthlyGrowth); result.put("topReferrers", topReferrers);
        return ApiResponse.of(result);
    }
    @GetMapping(value = "/export.csv", produces = "text/csv") public ResponseEntity<byte[]> export() {
        requireAdmin(); StringBuilder csv = new StringBuilder("requestId,userId,userName,userType,bankName,accountNumber,ifsc,amount,status,requestDate\n");
        redemptionService.all().forEach(r -> csv.append(r.getId()).append(',').append(r.getUser().getId()).append(',').append(quote(r.getUser().getFullName())).append(',').append(r.getUser().getRole()).append(',').append(quote(r.getBankName())).append(',').append(quote(redemptionService.decryptedAccount(r))).append(',').append(quote(redemptionService.decryptedIfsc(r))).append(',').append(r.getAmount()).append(',').append(r.getStatus()).append(',').append(r.getCreatedAt()).append('\n'));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=redemptions.csv").body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Map<String, Object> adminRedemptionView(RedemptionRequest r) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("requestId", r.getId()); value.put("userId", r.getUser().getId()); value.put("userName", r.getUser().getFullName()); value.put("userType", r.getUser().getRole()); value.put("mobileNumber", r.getUser().getMobile()); value.put("bankName", r.getBankName()); value.put("accountHolderName", r.getAccountHolderName()); value.put("accountNumber", redemptionService.decryptedAccount(r)); value.put("ifsc", redemptionService.decryptedIfsc(r)); value.put("requestedAmount", r.getAmount()); value.put("requestDate", r.getCreatedAt()); value.put("status", r.getStatus()); value.put("paymentReference", r.getPaymentReference()); return value;
    }
    private Map<String, Object> referralView(Referral r) { return Map.of("id", r.getId(), "referrer", r.getReferrer().getFullName(), "referredUser", r.getReferredUser().getFullName(), "registrationDate", r.getRegisteredAt(), "coinsAwarded", r.getCoinsAwarded(), "status", r.getStatus()); }
    private String quote(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private void requireAdmin() { if (authFacade.currentUser().getRole() != Role.ADMIN) throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required"); }
}
