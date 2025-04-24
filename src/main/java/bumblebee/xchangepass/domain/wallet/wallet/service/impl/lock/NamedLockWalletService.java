package bumblebee.xchangepass.domain.wallet.wallet.service.impl.lock;

import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.NamedLockRepository;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class NamedLockWalletService implements WalletService {
    private final WalletRepository walletRepository;
    private final WalletBalanceService balanceService;
    private final NamedLockRepository namedLockRepository;
    private final FraudDetectionService fraudDetectionService;
    private final ExchangeService exchangeService;
    private final UserService userService;

    @Override
    public String getType() {
        return "namedLock";
    }

    @Override
    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        BigDecimal chargeAmount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            chargeAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        }

        namedLockRepository.getLock(wallet.getWalletId());
        try {
            if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
                Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
                balanceService.createBalance(findWallet, request.toCurrency());
            }

            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.chargeBalance(balance, chargeAmount);
        } finally {
            // 트랜잭션 종료 시점에서 락을 해제하도록 변경
            Boolean unlockSuccess = namedLockRepository.releaseLock(wallet.getWalletId());
            if (!unlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] 사용자 ID: {}", userId);
            }
        }
    }

    @Override
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        BigDecimal amount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            amount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), amount);
        }

        namedLockRepository.getLock(wallet.getWalletId());
        try {
            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.withdrawBalance(balance, amount);

            return balance.getBalance();
        } finally {
            Boolean unlockSuccess = namedLockRepository.releaseLock(wallet.getWalletId());
            if (!unlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] 사용자 ID: {}", userId);
            }
        }


    }

    @Override
    @Transactional
    public void transfer(Long senderId, WalletTransferRequest request) {
        User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());

        Wallet fromWallet = walletRepository.findByUserId(senderId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        Wallet toWallet = walletRepository.findByUserId(receiver.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        // Deadlock 방지를 위해 ID 크기 순으로 Advisory Lock을 획득
        long smallerId = Math.min(fromWallet.getWalletId(), toWallet.getWalletId());
        long largerId = Math.max(fromWallet.getWalletId(), toWallet.getWalletId());

        namedLockRepository.getLock(smallerId);
        namedLockRepository.getLock(largerId);

        try {
            WalletBalance fromBalance = balanceService.findBalance(fromWallet.getWalletId(), request.fromCurrency());
            WalletBalance toBalance = balanceService.findBalance(toWallet.getWalletId(), request.toCurrency());

            BigDecimal transferAmount = request.transferAmount();

            if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            if (!request.toCurrency().equals(request.fromCurrency())) {
                transferAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.transferAmount());
            }

            fraudDetectionService.detect(new FraudDetectEvent(
                    senderId,
                    transferAmount,
                    LocalDateTime.now(),
                    null
            ));

            balanceService.transferBalance(fromBalance, toBalance, transferAmount);
        } finally {
            Boolean largeUnlockSuccess = namedLockRepository.releaseLock(largerId);
            if (!largeUnlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] Wallet ID: {}", largerId);
            }

            Boolean smallUnlockSuccess = namedLockRepository.releaseLock(smallerId);
            if (!smallUnlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] Wallet ID: {}", smallerId);
            }

        }
    }

    @Override
    @Transactional
    public List<WalletBalanceResponse> balance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        namedLockRepository.getLock(wallet.getWalletId());
        try {
            List<WalletBalance> balanceList = balanceService.findBalances(wallet.getWalletId());

            return balanceList.stream()
                    .map(balance -> new WalletBalanceResponse(
                            balance.getCurrency().getCurrencyCode(),
                            balance.getBalance()
                    ))
                    .toList();
        } finally {
            Boolean unlockSuccess = namedLockRepository.releaseLock(wallet.getWalletId());
            if (!unlockSuccess) {
                log.error("⚠️ [Named Lock 해제 실패] 사용자 ID: {}", userId);
            }
        }
    }
}