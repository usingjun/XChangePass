package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectEvent;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudAmountNormalizer;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudDetectionService;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudTransactionType;
import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.WalletPasswordResponse;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.TransactionAdvisoryLock;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final CardService cardService;
    private final WalletBalanceService balanceService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final TransactionAdvisoryLock advisoryLock;
    private final FraudAmountNormalizer fraudAmountNormalizer;
    private final FraudDetectionService fraudDetectionService;
    private final ExchangeService exchangeService;
    private final UserService userService;
    private final WalletTransferRepository walletTransferRepository;

    @Transactional
    public void createWallet(User user, String walletPassword) {
        Wallet wallet = new Wallet(user, walletPassword);

        user.changeWallet(walletRepository.save(wallet));
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));

        // ✅ 모바일 카드 발급 (동기 처리)
        cardService.generateMobileCard(wallet);
    }

    public WalletPasswordResponse checkWalletPassword(Long userId, String rawPassword) {
        try {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            if (!bCryptPasswordEncoder.matches(rawPassword, wallet.getWalletPassword())) {
                throw new CommonException(ErrorCode.INVALID_WALLET_PASSWORD);
            }

            return new WalletPasswordResponse(true); // ✅ 일치
        } catch (CommonException e) {
            return new WalletPasswordResponse(false); // ✅ 불일치
        }
    }

    @Override
    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        Wallet wallet = findWalletByUserId(userId);
        BigDecimal chargeAmount = exchange(request.fromCurrency(), request.toCurrency(), request.amount());

        advisoryLock.acquire(wallet.getWalletId());
        if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
            balanceService.createBalance(wallet, request.toCurrency());
        }

        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
        balanceService.chargeBalance(balance, chargeAmount);
    }

    @Override
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        Wallet wallet = findWalletByUserId(userId);
        BigDecimal amount = exchange(request.fromCurrency(), request.toCurrency(), request.amount());

        advisoryLock.acquire(wallet.getWalletId());
        WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
        balanceService.withdrawBalance(balance, amount);
        return balance.getBalance();
    }

    @Override
    @Transactional
    public void transfer(Long senderId, WalletTransferRequest request) {
        executeTransfer(null, senderId, request);
    }

    @Override
    @Transactional
    public WalletTransferResponse transfer(UUID transferId, Long senderId, Long receiverId,
                                           WalletTransferRequest request) {
        WalletTransfer transfer = walletTransferRepository.findById(transferId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
        executeMoneyTransfer(transferId, senderId, receiverId, request);
        transfer.complete();
        return new WalletTransferResponse(transfer.getTransferId(), transfer.getStatus());
    }

    private void executeTransfer(UUID transferId, Long senderId, WalletTransferRequest request) {
        User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());
        BigDecimal normalizedAmount = fraudAmountNormalizer.normalize(
                request.transferAmount(), request.fromCurrency()
        );
        fraudDetectionService.verify(new FraudDetectEvent(
                senderId, normalizedAmount, LocalDateTime.now(), null, FraudTransactionType.WALLET
        ));

        executeMoneyTransfer(transferId, senderId, receiver.getUserId(), request);
    }

    private void executeMoneyTransfer(UUID transferId, Long senderId, Long receiverId,
                                      WalletTransferRequest request) {
        Wallet fromWallet = findWalletByUserId(senderId);
        Wallet toWallet = findWalletByUserId(receiverId);

        acquireInOrder(fromWallet.getWalletId(), toWallet.getWalletId());

        WalletBalance fromBalance = balanceService.findBalance(fromWallet.getWalletId(), request.fromCurrency());
        WalletBalance toBalance = balanceService.findBalance(toWallet.getWalletId(), request.toCurrency());
        BigDecimal receivedAmount = exchange(request.fromCurrency(), request.toCurrency(), request.transferAmount());

        if (request.transferAmount().compareTo(fromBalance.getBalance()) > 0) {
            throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
        }

        balanceService.transferBalance(
                fromBalance, toBalance, request.transferAmount(), receivedAmount, transferId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletBalanceResponse> balance(Long userId) {
        Wallet wallet = findWalletByUserId(userId);
        return balanceService.findBalances(wallet.getWalletId()).stream()
                .map(balance -> new WalletBalanceResponse(
                        balance.getCurrency().getCurrencyCode(),
                        balance.getBalance()
                ))
                .toList();
    }

    private Wallet findWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
    }

    private BigDecimal exchange(Currency from, Currency to, BigDecimal amount) {
        return from.equals(to) ? amount : exchangeService.getExchangeMoney(from, to, amount);
    }

    private void acquireInOrder(Long firstWalletId, Long secondWalletId) {
        long smallerId = Math.min(firstWalletId, secondWalletId);
        long largerId = Math.max(firstWalletId, secondWalletId);
        advisoryLock.acquire(smallerId);
        if (smallerId != largerId) {
            advisoryLock.acquire(largerId);
        }
    }
}
