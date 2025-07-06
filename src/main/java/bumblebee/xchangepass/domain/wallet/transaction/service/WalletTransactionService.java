package bumblebee.xchangepass.domain.wallet.transaction.service;

import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import bumblebee.xchangepass.domain.transaction.mapper.TransactionMetadataMapper;
import bumblebee.xchangepass.domain.transaction.service.RedisTransactionQueueService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private static final String REDIS_KEY_PREFIX = "transactions:insert:";
    private final WalletRepository walletRepository;
    private final RedisTransactionQueueService redisTransactionQueueService;

    @Transactional
    public void saveTransaction(Long myWalletId, Long counterWalletId, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType) {
        Wallet myWallet = walletRepository.findById(myWalletId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);
        User sender = myWallet.getUser();

        if (transactionType == WalletTransactionType.TRANSFER && counterWalletId == null)
            throw ErrorCode.RECEIVER_NOT_FOUND.commonException();

        User receiver = (counterWalletId != null)
                ? walletRepository.findById(counterWalletId)
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException).getUser()
                : null;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("amount", amount);
        metadata.put("type", TransactionType.WALLET);
        metadata.put("walletType", transactionType.name());
        if (receiver != null) {
            metadata.put("receiver", receiver.getUserId());
        }

        TransactionResponse response = new TransactionResponse(
                sender.getUserId(),
                fromCurrency,
                toCurrency,
                LocalDateTime.now(),
                TransactionMetadataMapper.mapToDto(metadata)
        );

        String redisKey = REDIS_KEY_PREFIX + sender.getUserId();
        redisTransactionQueueService.enqueue(redisKey, response);

    }

}