package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferIdempotencyService;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferRequestHasher;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferReservation;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferReservationWriter;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WalletTransferIdempotencyServiceTest {

    private WalletTransferReservationWriter writer;
    private WalletTransferRepository repository;
    private WalletTransferRequestHasher hasher;
    private WalletTransferIdempotencyService service;

    @BeforeEach
    void setUp() {
        writer = mock(WalletTransferReservationWriter.class);
        repository = mock(WalletTransferRepository.class);
        hasher = new WalletTransferRequestHasher();
        service = new WalletTransferIdempotencyService(writer, repository, hasher);
    }

    @Test
    void insertWinnerOwnsExecution() {
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = request(BigDecimal.TEN);
        WalletTransfer transfer = transfer(key, request);
        when(writer.create(1L, key, hasher.hash(request))).thenReturn(transfer);

        WalletTransferReservation result = service.reserve(1L, key, request);

        assertThat(result.owner()).isTrue();
        assertThat(result.transferId()).isEqualTo(transfer.getTransferId());
    }

    @Test
    void completedDuplicateReturnsExistingResult() {
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = request(BigDecimal.TEN);
        WalletTransfer transfer = transfer(key, request);
        transfer.startValidating();
        transfer.startProcessing();
        transfer.complete();
        duplicateReturns(key, transfer);

        WalletTransferReservation result = service.reserve(1L, key, request);

        assertThat(result.owner()).isFalse();
        assertThat(result.existingResponse().transferId()).isEqualTo(transfer.getTransferId());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        UUID key = UUID.randomUUID();
        WalletTransferRequest original = request(BigDecimal.TEN);
        WalletTransfer transfer = transfer(key, original);
        duplicateReturns(key, transfer);

        assertThatThrownBy(() -> service.reserve(1L, key, request(BigDecimal.ONE)))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void failedDuplicateReturnsOriginalBusinessFailure() {
        UUID key = UUID.randomUUID();
        WalletTransferRequest request = request(BigDecimal.TEN);
        WalletTransfer transfer = transfer(key, request);
        transfer.fail(ErrorCode.BALANCE_NOT_AVAILABLE,
                WalletTransferFailureStage.BALANCE_VALIDATION, false);
        duplicateReturns(key, transfer);

        assertThatThrownBy(() -> service.reserve(1L, key, request))
                .isInstanceOf(CommonException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BALANCE_NOT_AVAILABLE);
    }

    private WalletTransfer transfer(UUID key, WalletTransferRequest request) {
        return new WalletTransfer(UUID.randomUUID(), 1L, key, hasher.hash(request));
    }

    private void duplicateReturns(UUID key, WalletTransfer transfer) {
        when(writer.create(anyLong(), eq(key), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findBySenderUserIdAndIdempotencyKey(1L, key))
                .thenReturn(Optional.of(transfer));
    }

    private WalletTransferRequest request(BigDecimal amount) {
        return new WalletTransferRequest(
                "receiver", "010-1234-5678", amount,
                Currency.getInstance("KRW"), Currency.getInstance("KRW"),
                null, WalletTransferType.GENERAL
        );
    }
}
