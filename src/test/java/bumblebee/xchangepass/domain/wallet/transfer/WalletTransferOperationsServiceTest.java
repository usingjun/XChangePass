package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferOperationsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletTransferOperationsServiceTest {

    @Test
    void returnsStaleActiveTransfersWithoutChangingThem() {
        WalletTransferRepository transfers = mock(WalletTransferRepository.class);
        WalletTransfer active = transfer();
        ReflectionTestUtils.setField(active, "updatedAt", LocalDateTime.now().minusMinutes(10));
        when(transfers.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(anyCollection(), any()))
                .thenReturn(List.of(active));
        WalletTransferOperationsService service = service(transfers, mock(WalletTransactionRepository.class),
                new SimpleMeterRegistry());

        var stale = service.findStaleTransfers();

        assertThat(stale).singleElement().satisfies(result -> {
            assertThat(result.transferId()).isEqualTo(active.getTransferId());
            assertThat(result.status()).isEqualTo(WalletTransferStatus.REQUESTED);
        });
        assertThat(active.getStatus()).isEqualTo(WalletTransferStatus.REQUESTED);
    }

    @Test
    void reportsCompletedTransferWithoutExactlyOneLedger() {
        WalletTransferRepository transfers = mock(WalletTransferRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WalletTransfer completed = transfer();
        completed.startValidating();
        completed.startProcessing();
        completed.complete();
        when(transfers.findAll()).thenReturn(List.of(completed));
        when(transactions.countByTransferId(completed.getTransferId())).thenReturn(0L);
        WalletTransferOperationsService service = service(transfers, transactions, meterRegistry);

        var issues = service.findReconciliationIssues();

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.ledgerCount()).isZero();
            assertThat(issue.expectedLedgerCount()).isEqualTo(1L);
        });
        assertThat(meterRegistry.counter("wallet.transfer.reconciliation.mismatches").count())
                .isEqualTo(1.0);
    }

    private WalletTransferOperationsService service(WalletTransferRepository transfers,
                                                     WalletTransactionRepository transactions,
                                                     SimpleMeterRegistry meterRegistry) {
        WalletTransferOperationsService service = new WalletTransferOperationsService(
                transfers, transactions, meterRegistry
        );
        ReflectionTestUtils.setField(service, "staleThresholdMinutes", 5L);
        return service;
    }

    private WalletTransfer transfer() {
        return new WalletTransfer(UUID.randomUUID(), 1L, UUID.randomUUID(), "a".repeat(64));
    }
}
