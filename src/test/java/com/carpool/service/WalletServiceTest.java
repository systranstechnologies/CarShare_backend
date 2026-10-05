package com.carpool.service;

import com.carpool.entity.UserWallet;
import com.carpool.exception.AppException;
import com.carpool.repository.UserRepository;
import com.carpool.repository.UserWalletRepository;
import com.carpool.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    private WalletService service;
    private UUID userId;
    private UserWallet wallet;

    @BeforeEach void setUp() {
        service = new WalletService(walletRepository, transactionRepository, userRepository);
        userId = UUID.randomUUID();
        wallet = new UserWallet();
        wallet.setId(UUID.randomUUID());
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void creditsWalletAndCreatesLedgerEntryOnce() {
        when(transactionRepository.existsByIdempotencyKey("reward:1")).thenReturn(false);
        service.credit(userId, 100, "REFERRAL_REWARD", "REFERRAL", UUID.randomUUID(), "reward:1", "test");
        assertEquals(100, wallet.getAvailableCoins());
        assertEquals(100, wallet.getTotalEarnedCoins());
        verify(transactionRepository).save(any());
    }

    @Test void rejectsDebitThatWouldMakeBalanceNegative() {
        wallet.setAvailableCoins(20);
        when(transactionRepository.existsByIdempotencyKey("debit:1")).thenReturn(false);
        assertThrows(AppException.class, () -> service.debit(userId, 21, "ADMIN_ADJUSTMENT", "ADMIN", UUID.randomUUID(), "debit:1", "test"));
        assertEquals(20, wallet.getAvailableCoins());
        verify(transactionRepository, never()).save(any());
    }
}
