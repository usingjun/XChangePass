package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletTransferLifecycleServiceTest {

    private WalletTransferRepository repository;
    private WalletTransferLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = mock(WalletTransferRepository.class);
        TransactionStatusEventService eventService = mock(TransactionStatusEventService.class);
        service = new WalletTransferLifecycleService(repository, eventService);
    }

    @Test
    void assignReceiverStoresReceiverUserIdOnTransfer() {
        UUID transferId = UUID.randomUUID();
        WalletTransfer transfer = new WalletTransfer(transferId, 1L, UUID.randomUUID(), "a".repeat(64));
        transfer.startValidating();
        when(repository.findById(transferId)).thenReturn(Optional.of(transfer));

        service.assignReceiver(transferId, 2L);

        assertThat(transfer.getReceiverUserId()).isEqualTo(2L);
    }
}
