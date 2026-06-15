package bumblebee.xchangepass.domain.transaction.scheduler;

import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import bumblebee.xchangepass.domain.exchangeTransaction.entitiy.ExchangeTransaction;
import bumblebee.xchangepass.domain.exchangeTransaction.repository.ExchangeTransactionRepository;
import bumblebee.xchangepass.domain.transaction.entity.ProjectionStatus;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionProjectionScheduler {

    private static final List<ProjectionStatus> TARGET_STATUSES =
            List.of(ProjectionStatus.PENDING, ProjectionStatus.RETRY);
    private static final PageRequest BATCH_SIZE = PageRequest.of(0, 100);
    private final WalletTransactionRepository walletTransactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final TransactionService transactionService;

    @Transactional
    @Scheduled(fixedDelay = 10_000)
    public void projectPendingTransactions() {
        LocalDateTime now = LocalDateTime.now();
        projectWalletTransactions(walletTransactionRepository.findProjectionTargets(TARGET_STATUSES, now, BATCH_SIZE));
        projectCardTransactions(cardTransactionRepository.findProjectionTargets(TARGET_STATUSES, now, BATCH_SIZE));
        projectExchangeTransactions(exchangeTransactionRepository.findProjectionTargets(TARGET_STATUSES, now, BATCH_SIZE));
    }

    private void projectWalletTransactions(List<WalletTransaction> transactions) {
        for (WalletTransaction transaction : transactions) {
            try {
                transactionService.projectToMongo(transaction);
                transaction.getProjection().projected();
            } catch (Exception e) {
                transaction.getProjection().failed(e.getMessage(), LocalDateTime.now());
                log.warn("Wallet transaction projection failed. transactionId={}", transaction.getTransactionId(), e);
            }
        }
    }

    private void projectCardTransactions(List<CardTransaction> transactions) {
        for (CardTransaction transaction : transactions) {
            try {
                transactionService.projectToMongo(transaction);
                transaction.getProjection().projected();
            } catch (Exception e) {
                transaction.getProjection().failed(e.getMessage(), LocalDateTime.now());
                log.warn("Card transaction projection failed. transactionId={}", transaction.getTransactionId(), e);
            }
        }
    }

    private void projectExchangeTransactions(List<ExchangeTransaction> transactions) {
        for (ExchangeTransaction transaction : transactions) {
            try {
                transactionService.projectToMongo(transaction);
                transaction.getProjection().projected();
            } catch (Exception e) {
                transaction.getProjection().failed(e.getMessage(), LocalDateTime.now());
                log.warn("Exchange transaction projection failed. transactionId={}", transaction.getTransactionId(), e);
            }
        }
    }
}
