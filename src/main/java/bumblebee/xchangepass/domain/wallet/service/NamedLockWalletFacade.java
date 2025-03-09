package bumblebee.xchangepass.domain.wallet.service;

import bumblebee.xchangepass.domain.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.repository.LockRepository;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    public void charge(WalletInOutRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.userId());

        if (wallet == null) {
            throw ErrorCode.WALLET_NOT_FOUND.commonException();
        }

        // Advisory Lock을 잡기 전에 트랜잭션이 유지되도록 보장
        lockRepository.getLock(wallet.getWalletId());

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
            lockRepository.releaseLock(wallet.getWalletId());
        }
    }

    @Transactional
    public BigDecimal withdrawal(WalletInOutRequest request) {
        Wallet wallet = null;
        WalletBalance balance = null;
        try {
            wallet = walletRepository.findByUserId(request.userId());

            // Advisory Lock 획득
            lockRepository.getLock(wallet.getWalletId());

            balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.withdrawBalance(balance, request.amount());

        } finally {
            if (wallet != null) {
                lockRepository.releaseLock(wallet.getWalletId());
            }
        }
        return balance.getBalance();
    }

    @Transactional
    public void transfer(WalletTransferRequest request) {
        Wallet fromWallet = walletRepository.findById(request.senderWalletId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        Wallet toWallet = walletRepository.findById(request.receiverWalletId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        // Deadlock 방지를 위해 ID 크기 순으로 Advisory Lock을 획득
        long smallerId = Math.min(fromWallet.getWalletId(), toWallet.getWalletId());
        long largerId = Math.max(fromWallet.getWalletId(), toWallet.getWalletId());

        lockRepository.getLock(smallerId);
        lockRepository.getLock(largerId);

        try {
            WalletBalance fromBalance = balanceService.findBalance(fromWallet.getWalletId(), request.fromCurrency());
            WalletBalance toBalance = balanceService.findBalance(toWallet.getWalletId(), request.toCurrency());

            BigDecimal transferAmount = request.transferAmount();

            if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.transferBalance(fromBalance, toBalance, transferAmount);
        } finally {
            lockRepository.releaseLock(largerId);
            lockRepository.releaseLock(smallerId);
        }
    }
}