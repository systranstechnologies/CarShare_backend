package com.carpool.service;

import com.carpool.dto.rewards.CreateRedemptionRequest;
import com.carpool.dto.rewards.UpdateRedemptionRequest;
import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.*;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedemptionService {
    private final RedemptionRequestRepository redemptionRepository;
    private final RedemptionHistoryRepository historyRepository;
    private final RewardSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final BankDataCryptoService cryptoService;
    private final AuthFacade authFacade;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public RedemptionRequest submit(CreateRedemptionRequest request) {
        UUID userId = authFacade.currentUser().getUserId();
        String account = request.getAccountNumber().trim();
        if (!account.equals(request.getConfirmAccountNumber().trim())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ACCOUNT_MISMATCH", "Account numbers do not match");
        }
        RewardSettings settings = settingsRepository.findById((short) 1).orElseThrow();
        if (request.getRedeemAmount() < settings.getMinimumRedemption() || request.getRedeemAmount() > settings.getMaximumRedemption()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "REDEMPTION_LIMIT", "Redeem amount must be between " + settings.getMinimumRedemption() + " and " + settings.getMaximumRedemption());
        }
        User user = userRepository.findById(userId).orElseThrow();
        RedemptionRequest redemption = new RedemptionRequest();
        redemption.setUser(user);
        redemption.setWallet(walletService.getWallet(userId));
        redemption.setAccountHolderName(request.getAccountHolderName().trim());
        redemption.setBankName(request.getBankName().trim());
        redemption.setAccountNumberEncrypted(cryptoService.encrypt(account));
        redemption.setAccountNumberLast4(account.substring(account.length() - 4));
        redemption.setIfscEncrypted(cryptoService.encrypt(request.getIfscCode().trim().toUpperCase(Locale.ROOT)));
        redemption.setAmount(request.getRedeemAmount());
        redemption = redemptionRepository.save(redemption);
        walletService.holdForRedemption(userId, redemption.getAmount(), redemption.getId());
        addHistory(redemption, null, "PENDING", userId, "Submitted by user");
        notificationService.create(userId, NotificationType.REDEMPTION_SUBMITTED,
            "Redemption received", "Your redemption request has been received.", "/referrals");
        auditService.log("REDEMPTION_SUBMITTED", userId.toString(), redemption.getId().toString(), "{\"amount\":" + redemption.getAmount() + "}");
        return redemption;
    }

    @Transactional
    public RedemptionRequest transition(UUID id, UpdateRedemptionRequest request) {
        requireAdmin();
        RedemptionRequest redemption = redemptionRepository.findByIdWithUser(id)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "REDEMPTION_NOT_FOUND", "Redemption request not found"));
        String from = redemption.getStatus();
        String to = request.getStatus().trim().toUpperCase(Locale.ROOT);
        boolean valid = (from.equals("PENDING") && (to.equals("PROCESSING") || to.equals("REJECTED")))
            || (from.equals("PROCESSING") && (to.equals("PAID") || to.equals("REJECTED")))
            || (from.equals("PAID") && to.equals("CLOSED"));
        if (!valid) throw new AppException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Invalid redemption status transition");
        UUID adminId = authFacade.currentUser().getUserId();
        redemption.setStatus(to);
        redemption.setReviewedBy(adminId);
        redemption.setReviewedAt(Instant.now());
        if (to.equals("REJECTED")) {
            if (request.getNote() == null || request.getNote().isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED", "Rejection reason is required");
            redemption.setRejectionReason(request.getNote().trim());
            walletService.releaseRedemption(redemption.getUser().getId(), redemption.getAmount(), redemption.getId());
            notificationService.create(redemption.getUser().getId(), NotificationType.REDEMPTION_REJECTED,
                "Redemption rejected", "Your redemption request was rejected. Coins have been returned to your wallet.", "/referrals");
        } else if (to.equals("PAID")) {
            if (request.getPaymentReference() == null || request.getPaymentReference().isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "PAYMENT_REFERENCE_REQUIRED", "Payment reference is required");
            redemption.setPaymentReference(request.getPaymentReference().trim());
            redemption.setPaidAt(Instant.now());
            walletService.settleRedemption(redemption.getUser().getId(), redemption.getAmount(), redemption.getId());
            notificationService.create(redemption.getUser().getId(), NotificationType.REDEMPTION_APPROVED,
                "Redemption processed", "Your redemption payment has been processed.", "/referrals");
        } else if (to.equals("CLOSED")) {
            if (redemption.getPaymentReference() == null || redemption.getPaymentReference().isBlank()) {
                throw new AppException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_REQUIRED", "Add the payment reference before closing this redemption");
            }
            redemption.setClosedAt(Instant.now());
        }
        redemptionRepository.save(redemption);
        addHistory(redemption, from, to, adminId, request.getNote());
        auditService.log("REDEMPTION_" + to, adminId.toString(), id.toString(), "{}");
        return redemption;
    }

    public List<RedemptionRequest> mine() { return redemptionRepository.findByUserIdOrderByCreatedAtDesc(authFacade.currentUser().getUserId()); }
    public List<RedemptionRequest> all() { requireAdmin(); return redemptionRepository.findAllByOrderByCreatedAtDesc(); }
    public String decryptedAccount(RedemptionRequest request) { requireAdmin(); return cryptoService.decrypt(request.getAccountNumberEncrypted()); }
    public String decryptedIfsc(RedemptionRequest request) { requireAdmin(); return cryptoService.decrypt(request.getIfscEncrypted()); }

    private void addHistory(RedemptionRequest request, String from, String to, UUID actor, String note) {
        RedemptionHistory history = new RedemptionHistory();
        history.setRedemption(request); history.setFromStatus(from); history.setToStatus(to); history.setChangedBy(actor); history.setNote(note);
        historyRepository.save(history);
    }
    private void requireAdmin() {
        if (authFacade.currentUser().getRole() != Role.ADMIN) throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
    }
}
