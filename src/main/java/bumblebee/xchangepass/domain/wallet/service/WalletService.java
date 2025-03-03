package bumblebee.xchangepass.domain.wallet.service;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletChargeRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletTransactionResponse;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import bumblebee.xchangepass.domain.walletBalance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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

    @Transactional
    public void createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            return;//오류발생시키기
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
    public void charge(WalletChargeRequest request) {
        if (!request.toCurrency().equals(request.fromCurrency())) {
            //환전

        }

        System.out.println("충전시작");
        Wallet wallet = walletRepository.findByUserId(request.userId());

        if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
            Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
            balanceService.createBalance(findWallet, request.toCurrency());
        }

        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
        balanceService.chargeBalance(balance, request.chargeAmount());
    }

    @Transactional

    public BigDecimal withdrawal(WalletChargeRequest request) {
        if (!request.toCurrency().equals(request.fromCurrency())) {
            //환전

        }

        Wallet wallet = walletRepository.findByUserId(request.userId());
        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());

        if (request.chargeAmount().compareTo(balance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }


        balanceService.withdrawBalance(balance, request.chargeAmount());
        return balance.getBalance();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    @Transactional
    public void transfer(WalletTransferRequest request) {

        Wallet senderWallet = walletRepository.findById(request.senderWalletId())
                .orElseThrow(() -> new IllegalStateException("보내는 지갑이 존재하지 않습니다: " + request.senderWalletId()));

        Wallet receiverWallet = walletRepository.findById(request.receiverWalletId())
                .orElseThrow(() -> new IllegalStateException("받는 지갑이 존재하지 않습니다: " + request.receiverWalletId()));


        WalletBalance fromBalance = balanceService.findBalance(request.senderWalletId(), request.fromCurrency());
        WalletBalance toBalance = balanceService.findBalance(request.receiverWalletId(), request.toCurrency());

        System.out.println("request.toString( = " + request.toString());
        System.out.println("fromBalance = " + fromBalance.getBalanceId());
        System.out.println("fromBalance = " + fromBalance.getBalance());
        BigDecimal transferAmount = request.transferAmount();

        if (transferAmount.compareTo(fromBalance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        if (!request.fromCurrency().equals(request.toCurrency())) {
            //환전

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

//    @Transactional
//    public void transfer(WalletTransferRequest request) {
//        Wallet fromWallet = walletRepository.findByUserId(request.senderId());
//        Wallet toWallet = walletRepository.findByUserId(request.receiverId());
//
//        BigDecimal transferAmount = request.transferAmount();
//
//        // 보낸 사람 잔액 차감 (조건부 업데이트)
//        balanceService.withdrawBalanceWithCondition(
//                fromWallet.getWalletId(),
//                toWallet.getWalletId(),
//                request.fromCurrency(),
//                transferAmount
//        );
//
//    }
}
