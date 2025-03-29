package bumblebee.xchangepass.domain.wallet.wallet.service.impl.lock.redisson;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
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
    private final RedissonLock redissonLock;
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
            balanceService.chargeBalance(balance, request.amount());
        });
    }

    /**
     * 🔒 지갑 출금 (RedissonLock 적용)
     */
    @Override
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        String lockKey = "wallet:" + userId;
        return redissonLock.tryLock(lockKey, 10, 10, () -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());

            if (request.amount().compareTo(balance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.withdrawBalance(balance, request.amount());
            return balance.getBalance();
        });
    }

    /**
     * 🔒 지갑 송금 (멀티 락 적용)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(Long senderId, WalletTransferRequest request) {
        Wallet wallet = walletRepository.findByUserIdWithLock(senderId);
        String senderLockKey = "wallet:" + wallet.getWalletId();
        String receiverLockKey = "wallet:" + request.receiverWalletId();

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

            if (request.transferAmount().compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.transferBalance(fromBalance, toBalance, request.transferAmount());

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
