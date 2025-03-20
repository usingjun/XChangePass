package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.LockRepository;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
public class NamedLockWalletFacade {
    private final WalletRepository walletRepository;
    private final WalletBalanceService balanceService;
    private final LockRepository lockRepository;

    public NamedLockWalletFacade(WalletRepository walletRepository, WalletBalanceService walletBalanceService, LockRepository lockRepository) {
        this.walletRepository = walletRepository;
        this.balanceService = walletBalanceService;
        this.lockRepository = lockRepository;
    }

    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId);

        if (wallet == null) {
            throw ErrorCode.WALLET_NOT_FOUND.commonException();
        }

        Boolean lockAcquired = lockRepository.getLock(wallet.getWalletId());
        if (!lockAcquired) {
            log.error("⚠️ [Named Lock 실패] 사용자 ID: {}", userId);
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }

        try {
            if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
                Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
                balanceService.createBalance(findWallet, request.toCurrency());
            }

            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.chargeBalance(balance, request.amount());
        } finally {
            // 트랜잭션 종료 시점에서 락을 해제하도록 변경
            Boolean unlockSuccess = lockRepository.releaseLock(wallet.getWalletId());
            if (!unlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] 사용자 ID: {}", userId);
            }
        }
    }

    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId);
        if (wallet == null) {
            throw ErrorCode.WALLET_NOT_FOUND.commonException();
        }

        Boolean lockAcquired = lockRepository.getLock(wallet.getWalletId());
        if (!lockAcquired) {
            log.error("⚠️ [Named Lock 실패] 사용자 ID: {}", userId);
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }

        try {
            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.withdrawBalance(balance, request.amount());

            return balance.getBalance();
        }  finally {
            Boolean unlockSuccess = lockRepository.releaseLock(wallet.getWalletId());
            if (!unlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] 사용자 ID: {}", userId);
            }
        }


    }

    @Transactional
    public void transfer(Long senderId, WalletTransferRequest request) {
        Wallet fromWallet = walletRepository.findByUserId(senderId);
        Wallet toWallet = walletRepository.findById(request.receiverWalletId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        // Deadlock 방지를 위해 ID 크기 순으로 Advisory Lock을 획득
        long smallerId = Math.min(fromWallet.getWalletId(), toWallet.getWalletId());
        long largerId = Math.max(fromWallet.getWalletId(), toWallet.getWalletId());

        Boolean smallLockAcquired = lockRepository.getLock(smallerId);
        if (!smallLockAcquired) {
            log.error("⚠️ [Named Lock 획득 실패] Wallet ID: {}", smallerId);
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }

        Boolean largeLockAcquired = lockRepository.getLock(largerId);
        if (!largeLockAcquired) {
            log.error("⚠️ [Named Lock 획득 실패] Wallet ID: {}", largerId);
            lockRepository.releaseLock(smallerId); // 🔥 먼저 획득한 Lock 해제 후 예외 발생
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }

        try {
            WalletBalance fromBalance = balanceService.findBalance(fromWallet.getWalletId(), request.fromCurrency());
            WalletBalance toBalance = balanceService.findBalance(toWallet.getWalletId(), request.toCurrency());

            BigDecimal transferAmount = request.transferAmount();

            if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.transferBalance(fromBalance, toBalance, transferAmount);
        } finally {
            Boolean largeUnlockSuccess = lockRepository.releaseLock(largerId);
            if (!largeUnlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] Wallet ID: {}", largerId);
            }

            Boolean smallUnlockSuccess = lockRepository.releaseLock(smallerId);
            if (!smallUnlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] Wallet ID: {}", smallerId);
            }
        }
    }
}