package bumblebee.xchangepass.domain.wallet.wallet.service.redisson;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedissonLockService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletBalanceService balanceService;
    private final RedissonLock redissonLock; // RedissonLock을 주입받아 사용

    @Transactional
    public void createWallet(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new CommonException(ErrorCode.WALLET_ALREADY_EXIST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);
        // 나중에 수정 필요
        Wallet wallet = new Wallet(user, "1234");


        walletRepository.save(wallet);
        walletRepository.flush();
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));
    }

    /**
     * 🔒 지갑 충전 (RedissonLock 적용)
     */
    @Transactional
    public void charge(Long userId, WalletInOutRequest request) {
        String lockKey = "wallet:" + userId;
        redissonLock.tryLockVoid(lockKey, 10, 10, () -> {
            System.out.println("🔒 충전 시작");
            Wallet wallet = walletRepository.findByUserId(userId);

            if (!balanceService.checkBalance(wallet.getWalletId(), request.toCurrency())) {
                Wallet findWallet = walletRepository.findById(wallet.getWalletId())
                        .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
                balanceService.createBalance(findWallet, request.toCurrency());
            }

            WalletBalance balance = balanceService.findBalance(wallet.getWalletId(), request.toCurrency());
            balanceService.chargeBalance(balance, request.amount());
            System.out.println("🔓 충전 완료");
        });
    }

    /**
     * 🔒 지갑 출금 (RedissonLock 적용)
     */
    @Transactional
    public BigDecimal withdrawal(Long userId, WalletInOutRequest request) {
        String lockKey = "wallet:" + request.userId();
        return redissonLock.tryLock(lockKey, 10, 10, () -> {
            Wallet wallet = walletRepository.findByUserId(request.userId());
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transfer(WalletTransferRequest request) {
        String senderLockKey = "wallet:" + request.senderWalletId();
        String receiverLockKey = "wallet:" + request.receiverWalletId();

        RLock senderLock = redissonLock.getRedissonClient().getLock(senderLockKey);
        RLock receiverLock = redissonLock.getRedissonClient().getLock(receiverLockKey);
        RedissonMultiLock multiLock = new RedissonMultiLock(senderLock, receiverLock);

        boolean acquired = false;
        try {
            acquired = multiLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("🔴 송금 중 락을 획득하지 못했습니다.");
            }

            System.out.println("🔵 [송금 시작] " + request.senderWalletId() + " -> " + request.receiverWalletId());

            WalletBalance fromBalance = balanceService.findBalance(request.senderWalletId(), request.fromCurrency());
            WalletBalance toBalance = balanceService.findBalance(request.receiverWalletId(), request.toCurrency());

            if (request.transferAmount().compareTo(fromBalance.getBalance()) > 0) {
                throw ErrorCode.BALANCE_NOT_AVAILABLE.commonException();
            }

            balanceService.transferBalance(fromBalance, toBalance, request.transferAmount());

            System.out.println(balanceService.findBalance(request.receiverWalletId(), request.toCurrency()).getBalance());
            System.out.println(balanceService.findBalance(request.senderWalletId(), request.fromCurrency()).getBalance());


        } catch (InterruptedException e) {
            throw new RuntimeException("🔴 락 획득 중 인터럽트 발생", e);
        } finally {
            if (acquired) {
                System.out.println("🔓 [Redisson Lock 해제] " + senderLockKey + " & " + receiverLockKey);
                multiLock.unlock();
            }
        }
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
