package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransactionMessage;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Service
@RequiredArgsConstructor
public class WalletTransactionConsumer {

    private final WalletTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @RabbitListener(queues = "transaction-status-queue")
    @Transactional
    public void processTransaction(WalletTransactionMessage message) {
        Wallet myWallet = walletRepository.findById(message.myWalletId())
                .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

        Wallet counterWallet = (message.counterWalletId() != null)
                ? walletRepository.findById(message.counterWalletId()).orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException)
                : null;

        WalletTransaction transaction = new WalletTransaction(
                myWallet,
                counterWallet,
                message.amount(),
                message.fromCurrency() != null ? Currency.getInstance(message.fromCurrency()) : null,
                Currency.getInstance(message.toCurrency()),
                WalletTransactionType.valueOf(message.transactionType())
        );

        transaction.updateStatus(WalletTransactionStatus.SUCCESS); // ✅ 동기 처리에서 이미 성공한 거래

        transactionRepository.save(transaction);
    }

}
