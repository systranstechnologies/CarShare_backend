package com.carpool.service;

import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferralService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final ReferralProfileRepository profileRepository;
    private final ReferralRepository referralRepository;
    private final RewardSettingsRepository settingsRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ReferralProfile initializeUser(User user, String referralCode, String deviceFingerprint) {
        ReferralProfile profile = profileRepository.findByUserId(user.getId()).orElseGet(() -> createProfile(user));
        walletService.ensureWallet(user);
        if (referralCode != null && !referralCode.isBlank() && referralRepository.findByReferredUserId(user.getId()).isEmpty()) {
            RewardSettings settings = settings();
            if (!settings.isReferralEnabled()) return profile;
            ReferralProfile referrerProfile = profileRepository.findByReferralCodeIgnoreCase(referralCode.trim())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "INVALID_REFERRAL_CODE", "Referral code is invalid"));
            if (referrerProfile.getUser().getId().equals(user.getId()) || referrerProfile.getUser().getMobile().equals(user.getMobile())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "SELF_REFERRAL", "Self-referrals are not allowed");
            }
            Referral referral = new Referral();
            referral.setReferrer(referrerProfile.getUser());
            referral.setReferredUser(user);
            referral.setReferredRole(user.getRole());
            referral.setReferralCode(referrerProfile.getReferralCode());
            referral.setRegisteredAt(Instant.now());
            referral.setDeviceFingerprintHash(hash(deviceFingerprint));
            if (deviceFingerprint != null && !deviceFingerprint.isBlank()
                && referralRepository.existsByDeviceFingerprintHashAndReferrerId(referral.getDeviceFingerprintHash(), referrerProfile.getUser().getId())) {
                referral.setStatus("CANCELLED");
                referral.setFraudReason("Duplicate device fingerprint for referrer");
            }
            referralRepository.save(referral);
            UUID referrerId = referrerProfile.getUser().getId();
            notifyAfterCommit(referrerId, NotificationType.REFERRAL_REGISTERED,
                "Referral joined", "Your referral has successfully joined CarShare247.");
            auditService.log("REFERRAL_REGISTERED", user.getId().toString(), referral.getId().toString(), "{\"status\":\"" + referral.getStatus() + "\"}");
        }
        return profile;
    }

    @Transactional
    public void qualifyAndReward(UUID referredUserId, String activityType, UUID activityId) {
        Referral referral = referralRepository.findByReferredUserIdForUpdate(referredUserId).orElse(null);
        if (referral == null || !"PENDING".equals(referral.getStatus())) return;
        RewardSettings settings = settings();
        if (!settings.isReferralEnabled()) return;
        Instant now = Instant.now();
        referral.setActivityCompletedAt(now);
        referral.setQualifiedAt(now);
        referral.setStatus("QUALIFIED");
        referralRepository.save(referral);
        notificationService.create(referral.getReferrer().getId(), NotificationType.REFERRAL_QUALIFIED,
            "Referral qualified", "Your referral completed the required activity and earned you reward coins.", "/referrals");
        long coins = settings.getCoinsPerReferral();
        walletService.credit(referral.getReferrer().getId(), coins, "REFERRAL_REWARD", "REFERRAL", referral.getId(),
            "referral-reward:" + referral.getId(), "Reward for " + activityType);
        referral.setCoinsAwarded(coins);
        referral.setRewardedAt(Instant.now());
        referral.setStatus("REWARDED");
        referralRepository.save(referral);
        notificationService.create(referral.getReferrer().getId(), NotificationType.COIN_REWARD_CREDITED,
            "Coins credited", "Congratulations! " + coins + " coins have been credited to your wallet.", "/referrals");
        auditService.log("REFERRAL_REWARDED", "system", referral.getId().toString(), "{\"activityId\":\"" + activityId + "\",\"coins\":" + coins + "}");
    }

    public RewardSettings settings() {
        return settingsRepository.findById((short) 1).orElseGet(() -> settingsRepository.save(new RewardSettings()));
    }

    public List<Referral> referralsFor(UUID userId) {
        return referralRepository.findByReferrerIdOrderByRegisteredAtDesc(userId);
    }

    @Transactional
    public ReferralProfile profileFor(UUID userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> createProfile(userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"))));
    }

    private ReferralProfile createProfile(User user) {
        for (int attempt = 0; attempt < 8; attempt++) {
            ReferralProfile profile = new ReferralProfile();
            profile.setUser(user);
            profile.setReferralCode(generateCode());
            try { return profileRepository.saveAndFlush(profile); }
            catch (DataIntegrityViolationException duplicate) { if (attempt == 7) throw duplicate; }
        }
        throw new IllegalStateException("Unable to generate referral code");
    }

    private String generateCode() {
        StringBuilder value = new StringBuilder("CAR247");
        for (int index = 0; index < 6; index++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return value.toString();
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void notifyAfterCommit(UUID userId, NotificationType type, String title, String body) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationService.create(userId, type, title, body, "/referrals");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { notificationService.create(userId, type, title, body, "/referrals"); }
        });
    }
}
