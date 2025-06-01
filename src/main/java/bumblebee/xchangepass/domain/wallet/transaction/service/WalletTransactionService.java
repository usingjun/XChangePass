package bumblebee.xchangepass.domain.wallet.transaction.service;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.transaction.dto.WalletTransactionMessage;
import bumblebee.xchangepass.domain.wallet.transaction.dto.request.WalletTransactionSearchCondition;
import bumblebee.xchangepass.domain.wallet.transaction.dto.response.WalletTransactionListResponse;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final RabbitTemplate rabbitTemplate;

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

        try {
            transactionRepository.save(new WalletTransaction(sender, receiver, amount, fromCurrency, toCurrency, transactionType, WalletTransactionStatus.SUCCESS));
        } catch (Exception e) {
            log.warn("거래 저장 실패. 재시도 큐로 전송합니다: {}", e.getMessage());
            rabbitTemplate.convertAndSend("wallet-transaction-queue", new WalletTransactionMessage(
                    sender.getUserId(),
                    receiver == null ? null : receiver.getUserId(),
                    amount,
                    fromCurrency != null ? fromCurrency.getCurrencyCode() : null,
                    toCurrency.getCurrencyCode(),
                    transactionType.name()
            ));
        }

    }

    @Transactional
    public List<WalletTransactionListResponse> getTransaction(Long userId, WalletTransactionSearchCondition cond, Pageable pageable) {
        return transactionRepository.search(userId, cond, pageable)
                .stream().map(WalletTransactionListResponse::fromEntity)
                .toList();
    }

}