package bumblebee.xchangepass.domain.wallet.service;

import bumblebee.xchangepass.domain.ExchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletTransactionResponse;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserRepository userRepository;
    private final WalletBalanceService balanceService;
    private final ExchangeService exchangeService;

    @Transactional
    public void createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new CommonException(ErrorCode.WALLET_ALREADY_EXIST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);
        Wallet wallet = new Wallet(user);


        walletRepository.save(wallet);
        walletRepository.flush();
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));
    }


    @Transactional
    public List<WalletTransactionResponse> transaction(Long userId) {


        return null;
    }


    @Transactional
    public void charge(WalletInOutRequest request) {
        BigDecimal chargeAmount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            chargeAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        }

        System.out.println("충전시작");
        Wallet wallet = walletRepository.findByUserId(request.userId());

        if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
            Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
            balanceService.createBalance(findWallet, request.toCurrency());
        }

        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
        balanceService.chargeBalance(balance, chargeAmount);
    }

    @Transactional
    public BigDecimal withdrawal(WalletInOutRequest request) {
        BigDecimal amount = request.amount();
        if (!request.toCurrency().equals(request.fromCurrency())) {
            amount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), amount);
        }

        Wallet wallet = walletRepository.findByUserId(request.userId());
        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());

        if (amount.compareTo(balance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }


        balanceService.withdrawBalance(balance, amount);
        return balance.getBalance();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    @Transactional
    public void transfer(WalletTransferRequest request) {
        WalletBalance fromBalance = balanceService.findBalance(request.senderWalletId(), request.fromCurrency());

        if (!balanceService.checkBalance(request.receiverWalletId(), request.toCurrency())) {
            Wallet wallet = walletRepository.findById(request.receiverWalletId())
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            balanceService.createBalance(wallet, request.toCurrency());
        }

        WalletBalance toBalance = balanceService.findBalance(request.receiverWalletId(), request.toCurrency());

        BigDecimal transferAmount = request.transferAmount();
        if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        if (!request.toCurrency().equals(request.fromCurrency())) {
            transferAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.transferAmount());
        }

        balanceService.transferBalance(fromBalance, toBalance, transferAmount);
    }


    @Transactional
    public List<WalletBalanceResponse> balance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);
        System.out.println("wallet.getWalletId() = " + wallet.getWalletId());

        List<WalletBalance> balanceList = balanceService.findBalances(wallet.getWalletId());
        System.out.println("balanceList.get(0) = " + balanceList.get(0));

        return balanceList.stream()
                .peek(balance -> System.out.println("Processing balance: " + balance.getBalanceId()))
                .map(balance -> new WalletBalanceResponse(
                        balance.currency.getCurrencyCode(),
                        balance.getBalance()
                ))
                .toList();
    }
}
