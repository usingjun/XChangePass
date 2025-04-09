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

    /**
     * 화폐별 잔액 생성
     * @param wallet
     * @param currency
     */
    public void createBalance(Wallet wallet, Currency currency) {
        if (balanceRepository.existsByCurrency(wallet.getWalletId(), currency)) {
            return;
        }

        WalletBalance balance = new WalletBalance(wallet, currency);
        balanceRepository.save(balance);
    }

    /**
     * 화폐별 잔액 조회
     * @param walletId
     * @param currency
     * @return
     */
    @Transactional(readOnly = true)
    public WalletBalance findBalance(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrency(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    /**
     * 비관적 락 적용, 화폐별 잔액 조회
     * @param walletId
     * @param currency
     * @return
     */
    @Transactional
    public WalletBalance findBalanceWithLock(Long walletId, Currency currency) {
        return balanceRepository.findByWalletIdAndCurrencyWithPessimisticLock(walletId, currency)
                .orElseThrow(ErrorCode.BALANCE_NOT_FOUND::commonException);
    }

    /**
     * 화폐별 잔액 목록 조회
     * @param walletId
     * @return
     */
    @Transactional(readOnly = true)
    public List<WalletBalance> findBalances(Long walletId) {
        return balanceRepository.findByWalletId(walletId);
    }

    /**
     * 비관적 락 적용, 화폐별 잔액 목록 조회
     * @param walletId
     * @return
     */
    @Transactional
    public List<WalletBalance> findBalancesWithLock(Long walletId) {
        return balanceRepository.findByWalletIdWithPessimisticLock(walletId);
    }

    /**
     * 충전된 화폐별 잔액이 있는지 확인
     * @param walletId
     * @param currency
     * @return
     */
    public boolean checkBalance(Long walletId, Currency currency) {
        return balanceRepository.existsByCurrency(walletId, currency);
    }

    /**
     * 화폐별 잔액 입금
     * @param balance
     * @param amount
     */
    public void chargeBalance(WalletBalance balance, BigDecimal amount) {
        balance.addBalance(amount);
        balanceRepository.save(balance);

        transactionService.saveTransaction(balance.getWallet().getWalletId(), null, amount, null, balance.getCurrency(), WalletTransactionType.DEPOSIT);
    }

    /**
     * 화폐별 잔액 출금
     * @param balance
     * @param amount
     */
    public void withdrawBalance(WalletBalance balance, BigDecimal amount) {
        if (amount.compareTo(balance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        balance.subtractBalance(amount);
        balanceRepository.save(balance);

        transactionService.saveTransaction(balance.getWallet().getWalletId(), null, amount, null, balance.getCurrency(), WalletTransactionType.WITHDRAWAL);
    }

    /**
     * 화폐별 잔액 송금
     * @param fromBalance
     * @param toBalance
     * @param amount
     */
    public void transferBalance(WalletBalance fromBalance, WalletBalance toBalance, BigDecimal amount) {
        fromBalance.subtractBalance(amount);
        toBalance.addBalance(amount);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        transactionService.saveTransaction(fromBalance.getWallet().getWalletId(), toBalance.getWallet().getWalletId(), amount, fromBalance.getCurrency(), toBalance.getCurrency(), WalletTransactionType.TRANSFER);
    }

}
