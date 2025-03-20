package bumblebee.xchangepass.domain.wallet.balance.service;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.service.WalletTransactionService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.repository.WalletBalanceRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletBalanceService {

    private final WalletBalanceRepository balanceRepository;
    private final WalletTransactionService transactionService;

    @Transactional
    public void createBalance(Wallet wallet, Currency currency) {
        if (balanceRepository.existsByCurrency(wallet.getWalletId(), currency)) {
            // 이미 존재하는 경우 생성하지 않음
            return;
        }

        WalletBalance balance = new WalletBalance(wallet, currency);
        balanceRepository.save(balance);
    }

    @Transactional
    public WalletBalance findBalance(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrency(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    @Transactional
    public WalletBalance findBalanceWithLock(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyWithPessimisticLock(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    @Transactional
    public List<WalletBalance> findBalances(Long walletId) {
        return balanceRepository.findByWalletId(walletId);
    }

    @Transactional
    public List<WalletBalance> findBalancesWithLock(Long walletId) {
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

        transactionService.saveTransaction(balance.getWallet(), null, amount, null, balance.getCurrency(), WalletTransactionType.DEPOSIT);
    }

    @Transactional
    public void withdrawBalance(WalletBalance balance, BigDecimal amount) {
        balance.subtractBalance(amount);
        balanceRepository.save(balance);

        transactionService.saveTransaction(balance.getWallet(), null, amount, null, balance.getCurrency(), WalletTransactionType.WITHDRAWAL);
    }

    @Transactional
    public void transferBalance(WalletBalance fromBalance, WalletBalance toBalance, BigDecimal amount) {
        fromBalance.subtractBalance(amount);
        toBalance.addBalance(amount);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        transactionService.saveTransaction(fromBalance.getWallet(), toBalance.getWallet(), amount, fromBalance.getCurrency(), toBalance.getCurrency(), WalletTransactionType.TRANSFER);
    }

}
