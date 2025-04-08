package bumblebee.xchangepass.domain.wallet.wallet.service.impl.lock.redisson;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonLockWalletService implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletBalanceService balanceService;
    private final ScheduledTransferService scheduledTransferService;
    private final RedissonLock redissonLock;
    private final ExchangeService exchangeService;
    private final UserService userService;

    @Override
    public String getType() {
        return "redissonLock";
    }

    /**
     * 🔒 지갑 충전 (RedissonLock 적용)
     */
    @Override
    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        BigDecimal chargeAmount;
        if (!request.toCurrency().equals(request.fromCurrency())) {
            chargeAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        } else {
            chargeAmount = request.amount();
        }

        String lockKey = "wallet:" + userId;
        redissonLock.tryLockVoid(lockKey, 10, 10, () -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
                Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
                balanceService.createBalance(findWallet, request.toCurrency());
            }

            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.chargeBalance(balance, chargeAmount);
        });
    }

    /**
     * 🔒 지갑 출금 (RedissonLock 적용)
     */
    @Override
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        BigDecimal amount;
        if (!request.toCurrency().equals(request.fromCurrency())) {
            amount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.amount());
        } else {
            amount = request.amount();
        }

        String lockKey = "wallet:" + userId;
        return redissonLock.tryLock(lockKey, 10, 10, () -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());

            if (request.amount().compareTo(balance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.withdrawBalance(balance, amount);
            return balance.getBalance();
        });
    }

    /**
     * 🔒 지갑 송금 (멀티 락 적용)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(Long senderId, WalletTransferRequest request) {
        User receiver = userService.readUser(request.receiverName(), request.receiverPhoneNumber());

        Wallet senderWallet = walletRepository.findByUserIdWithLock(senderId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        Wallet receiverWallet = walletRepository.findByUserIdWithLock(receiver.getUserId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        String senderLockKey = "senderWallet:" + senderWallet.getWalletId();
        String receiverLockKey = "senderWallet:" + receiverWallet.getWalletId();

        RLock senderLock = redissonLock.getRedissonClient().getLock(senderLockKey);
        RLock receiverLock = redissonLock.getRedissonClient().getLock(receiverLockKey);
        RedissonMultiLock multiLock = new RedissonMultiLock(senderLock, receiverLock);

        boolean acquired = false;
        try {
            acquired = multiLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw ErrorCode.LOCK_TIME_OUT.commonException();
            }

            WalletBalance fromBalance = balanceService.findBalance(senderWallet.getWalletId(), request.fromCurrency());
            WalletBalance toBalance = balanceService.findBalance(receiverWallet.getWalletId(), request.toCurrency());

            BigDecimal transferAmount = request.transferAmount();
            if (request.transferAmount().compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            if (!request.toCurrency().equals(request.fromCurrency())) {
                transferAmount = exchangeService.getExchangeMoney(request.fromCurrency(), request.toCurrency(), request.transferAmount());
            }

            balanceService.transferBalance(fromBalance, toBalance, transferAmount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorCode.THREAD_INTERRUPTED.commonException();
        } finally {
            if (acquired) {
                try {
                    multiLock.unlock(); // ✅ unlock 예외 처리 추가
                } catch (IllegalMonitorStateException e) {
                    log.error("⚠️ [MultiLock 해제 실패] senderId: {}, receiverId: {}", senderId, receiverWallet.getWalletId(), e);
                }
            }
        }

    }

    @Override
    @Transactional
    public List<WalletBalanceResponse> balance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        List<WalletBalance> balanceList = balanceService.findBalances(wallet.getWalletId());

        return balanceList.stream()
                .map(balance -> new WalletBalanceResponse(
                        balance.getCurrency().getCurrencyCode(),
                        balance.getBalance()
                ))
                .toList();
    }
}
