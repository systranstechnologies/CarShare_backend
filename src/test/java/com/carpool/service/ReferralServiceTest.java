package com.carpool.service;

import com.carpool.entity.*;
import com.carpool.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {
    @Mock ReferralProfileRepository profileRepository;
    @Mock ReferralRepository referralRepository;
    @Mock RewardSettingsRepository settingsRepository;
    @Mock WalletService walletService;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;
    @Mock UserRepository userRepository;
    private ReferralService service;

    @BeforeEach void setUp() {
        service = new ReferralService(profileRepository, referralRepository, settingsRepository, walletService, notificationService, auditService, userRepository);
    }

    @Test void completedRideRewardsPendingReferralExactlyOnce() {
        UUID referredId = UUID.randomUUID();
        User referrer = new User(); referrer.setId(UUID.randomUUID()); referrer.setRole(Role.PASSENGER); referrer.setMobile("9000000001");
        User referred = new User(); referred.setId(referredId); referred.setRole(Role.PASSENGER); referred.setMobile("9000000002");
        Referral referral = new Referral(); referral.setId(UUID.randomUUID()); referral.setReferrer(referrer); referral.setReferredUser(referred); referral.setStatus("PENDING"); referral.setRegisteredAt(Instant.now());
        RewardSettings settings = new RewardSettings(); settings.setReferralEnabled(true); settings.setCoinsPerReferral(100);
        when(referralRepository.findByReferredUserIdForUpdate(referredId)).thenReturn(Optional.of(referral), Optional.of(referral));
        when(settingsRepository.findById((short) 1)).thenReturn(Optional.of(settings));
        when(referralRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.qualifyAndReward(referredId, "PASSENGER_COMPLETED_RIDE", UUID.randomUUID());
        service.qualifyAndReward(referredId, "PASSENGER_COMPLETED_RIDE", UUID.randomUUID());

        assertEquals("REWARDED", referral.getStatus());
        assertEquals(100, referral.getCoinsAwarded());
        verify(walletService, times(1)).credit(eq(referrer.getId()), eq(100L), eq("REFERRAL_REWARD"), eq("REFERRAL"), eq(referral.getId()), anyString(), anyString());
    }
}
