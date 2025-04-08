package bumblebee.xchangepass.domain.wallet.balance.service;

import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletBalanceService {

    private final WalletBalanceRepository balanceRepository;
    private final WalletTransactionService transactionService;
    private final FraudDetectionService fraudDetectionService;

    public void createBalance(Wallet wallet, Currency currency) {
        if (balanceRepository.existsByCurrency(wallet.getWalletId(), currency)) {
            return;
        }

        WalletBalance balance = new WalletBalance(wallet, currency);
        balanceRepository.save(balance);
    }

    @Transactional(readOnly = true)
    public WalletBalance findBalance(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrency(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    @Transactional
    public WalletBalance findBalanceWithLock(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyWithPessimisticLock(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    @Transactional(readOnly = true)
    public List<WalletBalance> findBalances(Long walletId) {
        return balanceRepository.findByWalletId(walletId);
    }

    @Transactional
    public List<WalletBalance> findBalancesWithLock(Long walletId) {
        return balanceRepository.findByWalletIdWithPessimisticLock(walletId);
    }

    public boolean checkBalance(Long walletId, Currency currency) {
        return balanceRepository.existsByCurrency(walletId, currency);
    }

    public void chargeBalance(WalletBalance balance, BigDecimal amount) {
        balance.addBalance(amount);
        balanceRepository.save(balance);

        transactionService.saveTransaction(balance.getWallet().getWalletId(), null, amount, null, balance.getCurrency(), WalletTransactionType.DEPOSIT);
    }

    public void withdrawBalance(WalletBalance balance, BigDecimal amount) {
        if (amount.compareTo(balance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        balance.subtractBalance(amount);
        balanceRepository.save(balance);

        transactionService.saveTransaction(balance.getWallet().getWalletId(), null, amount, null, balance.getCurrency(), WalletTransactionType.WITHDRAWAL);
    }

    public void transferBalance(WalletBalance fromBalance, WalletBalance toBalance, BigDecimal amount) {
        fromBalance.subtractBalance(amount);
        toBalance.addBalance(amount);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        fraudDetectionService.detect(new FraudDetectEvent(
                fromBalance.getWallet().getUser().getUserId(),
                amount,
                LocalDateTime.now(),
                null
        ));

        transactionService.saveTransaction(fromBalance.getWallet().getWalletId(), toBalance.getWallet().getWalletId(), amount, fromBalance.getCurrency(), toBalance.getCurrency(), WalletTransactionType.TRANSFER);
    }

}
