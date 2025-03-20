package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletTransactionResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
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
import java.util.Currency;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final CardService cardService;
    private final WalletBalanceService balanceService;
    private final ExchangeService exchangeService;

    @Transactional
    public void createWallet(User user, String walletPassword) {
        Wallet wallet = new Wallet(user, walletPassword);

        user.changeWallet(walletRepository.save(wallet));
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));

        // ✅ 모바일 카드 발급 (동기 처리)
        cardService.generateMobileCard(wallet);
    }


    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        BigDecimal chargeAmount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            chargeAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        }

        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId);
            if (wallet == null) {
                throw ErrorCode.WALLET_NOT_FOUND.commonException();
            }

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

    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        BigDecimal amount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            amount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), amount);
        }

        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId);
            if (wallet == null) {
                throw ErrorCode.WALLET_NOT_FOUND.commonException();
            }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(Long senderId, WalletTransferRequest request) {
        try {
            Wallet senderWallet = walletRepository.findByUserIdWithLock(senderId);
            if (senderWallet == null) {
                throw ErrorCode.WALLET_NOT_FOUND.commonException();
            }
            WalletBalance fromBalance = balanceService.findBalanceWithLock(senderWallet.getWalletId(), request.fromCurrency());
            if (fromBalance == null) {
                throw ErrorCode.BALANCE_NOT_FOUND.commonException();
            }

            if (!balanceService.checkBalance(request.receiverWalletId(), request.toCurrency())) {
                Wallet wallet = walletRepository.findById(request.receiverWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

                balanceService.createBalance(wallet, request.toCurrency());
            }

            WalletBalance toBalance = balanceService.findBalanceWithLock(request.receiverWalletId(), request.toCurrency());

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


    @Transactional
    public List<WalletBalanceResponse> balance(Long userId) {
        try {
            Wallet wallet = walletRepository.findByUserIdWithLock(userId);
            if (wallet == null) {
                throw ErrorCode.WALLET_NOT_FOUND.commonException();
            }

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
