package bumblebee.xchangepass.domain.wallet.wallet.service.impl.lock;

import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PessimisticLockWalletService implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletBalanceService balanceService;
    private final ScheduledTransferService scheduledTransferService;
    private final ExchangeService exchangeService;
    private final UserService userService;

    @Override
    public String getType() {
        return "pessimisticLock";
    }

    @Override
    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        BigDecimal chargeAmount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            chargeAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        }

        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
                Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
                balanceService.createBalance(findWallet, request.toCurrency());
            }

            WalletBalance balance = balanceService.findBalanceWithLock(wallet.getWalletId(), request.toCurrency());
            balanceService.chargeBalance(balance, chargeAmount);
        } catch (LockTimeoutException | PessimisticLockException | CannotAcquireLockException e) {
            log.error("⚠️ [Lock 획득 실패] 사용자 ID: {}, 이유: {}", userId, e.getMessage());
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }

    }

    @Override
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        BigDecimal amount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            amount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), amount);
        }

        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            WalletBalance balance = balanceService.findBalanceWithLock(wallet.getWalletId(), request.toCurrency());
            if (balance == null) {
                throw ErrorCode.BALANCE_NOT_FOUND.commonException();
            }

            if (amount.compareTo(balance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.withdrawBalance(balance, amount);
            return balance.getBalance();
        } catch (LockTimeoutException | PessimisticLockException | CannotAcquireLockException e) {
            log.error("⚠️ [Lock 획득 실패] 사용자 ID: {}, 이유: {}", userId, e.getMessage());
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(Long senderId, WalletTransferRequest request) {
        try {
            User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());

            Wallet senderWallet = walletRepository.findByUserIdWithLock(senderId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
            Wallet receiverWallet = walletRepository.findByUserIdWithLock(receiver.getUserId())
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            WalletBalance fromBalance = balanceService.findBalanceWithLock(senderWallet.getWalletId(), request.fromCurrency());
            if (fromBalance == null) {
                throw ErrorCode.BALANCE_NOT_FOUND.commonException();
            }

            if (!balanceService.checkBalance(receiverWallet.getWalletId(), request.toCurrency())) {
                Wallet wallet = walletRepository.findById(receiverWallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

                balanceService.createBalance(wallet, request.toCurrency());
            }

            WalletBalance toBalance = balanceService.findBalanceWithLock(receiverWallet.getWalletId(), request.toCurrency());

            BigDecimal transferAmount = request.transferAmount();
            if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            if (!request.toCurrency().equals(request.fromCurrency())) {
                transferAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.transferAmount());
            }

            balanceService.transferBalance(fromBalance, toBalance, transferAmount);
        } catch (LockTimeoutException | PessimisticLockException | CannotAcquireLockException e) {
            log.error("⚠️ [Lock 획득 실패] 사용자 ID: {}, 이유: {}", senderId, e.getMessage());
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }
    }

    @Override
    @Transactional
    public List<WalletBalanceResponse> balance(Long userId) {
        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            List<WalletBalance> balanceList = balanceService.findBalancesWithLock(wallet.getWalletId());

            return balanceList.stream()
                    .map(balance -> new WalletBalanceResponse(
                            balance.getCurrency().getCurrencyCode(),
                            balance.getBalance()
                    ))
                    .toList();
        } catch (LockTimeoutException | PessimisticLockException | CannotAcquireLockException e) {
            log.error("⚠️ [Lock 획득 실패] 사용자 ID: {}, 이유: {}", userId, e.getMessage());
            throw ErrorCode.LOCK_TIME_OUT.commonException();
        }
    }
}
