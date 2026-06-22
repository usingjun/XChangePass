package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferRecoveryCaseWriter {

    private final WalletTransferRecoveryCaseRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(UUID transferId, WalletTransferRecoveryCaseType type,
                       WalletTransferRecoveryCaseSeverity severity,
                       WalletTransferStatus observedStatus, Long observedVersion,
                       long observedLedgerCount) {
        repository.saveAndFlush(new WalletTransferRecoveryCase(
                transferId, type, severity, observedStatus, observedVersion, observedLedgerCount
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reobserve(UUID transferId, WalletTransferRecoveryCaseType type,
                          WalletTransferRecoveryCaseSeverity severity,
                          WalletTransferStatus observedStatus, Long observedVersion,
                          long observedLedgerCount) {
        WalletTransferRecoveryCase recoveryCase = repository.findForUpdate(transferId, type)
                .orElseThrow(() -> new IllegalStateException("Recovery case disappeared after unique conflict"));
        recoveryCase.reobserve(severity, observedStatus, observedVersion, observedLedgerCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acknowledge(UUID caseId) {
        repository.findById(caseId).orElseThrow().acknowledge();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolve(UUID caseId, String note) {
        repository.findById(caseId).orElseThrow().resolve(note);
    }
}
