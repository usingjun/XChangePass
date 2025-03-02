package bumblebee.xchangepass.domain.walletBalance.service;

import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.repository.WalletBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletBalanceService {

    private final WalletBalanceRepository balanceRepository;

    @Transactional
    public void createBalance(Wallet wallet, Currency currency) {
        WalletBalance balance = new WalletBalance(wallet, currency);
        balanceRepository.save(balance);
    }

    @Transactional
    public WalletBalance findBalance(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyWithPessimisticLock(walletId, currency);
    }

    @Transactional
    public List<WalletBalance> findBalances(Long walletId) {
        return balanceRepository.findByWalletIdWithPessimisticLock(walletId);
    }

    @Transactional
    public boolean checkBalance(Long walletId, Currency currency) {
        return balanceRepository.existsByCurrency(walletId, currency);
    }

    @Transactional
    public void chargeBalance(WalletBalance balance, BigDecimal amount) {
        balance.addBalance(amount);
        balanceRepository.save(balance);
    }

    @Transactional
    public void withdrawBalance(WalletBalance balance, BigDecimal amount) {
        balance.subtractBalance(amount);
        balanceRepository.save(balance);
    }

    @Transactional
    public void transferBalance(WalletBalance fromBalance, WalletBalance toBalance, BigDecimal amount) {
        fromBalance.subtractBalance(amount);
        toBalance.addBalance(amount);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);
    }

    @Transactional
    public void withdrawBalanceWithCondition(Long fromWalletId, Long toWalletId, Currency currency, BigDecimal amount) {
        int updatedRows = balanceRepository.withdrawWithCondition(fromWalletId, currency, amount);
        if (updatedRows == 0) {
            throw new IllegalStateException("BALANCE_NOT_AVAILABLE");
        }

        balanceRepository.deposit(toWalletId, currency, amount);
    }

}
