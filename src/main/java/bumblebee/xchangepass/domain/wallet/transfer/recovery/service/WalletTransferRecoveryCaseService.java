package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransferRecoveryCaseService {

    private final WalletTransferRecoveryCaseWriter writer;
    private final WalletTransferRecoveryMetrics metrics;

    public void record(WalletTransfer transfer, long ledgerCount,
                       WalletTransferRecoveryCaseType type,
                       WalletTransferRecoveryCaseSeverity severity) {
        try {
            writer.create(
                    transfer.getTransferId(), type, severity, transfer.getStatus(),
                    transfer.getVersion(), ledgerCount
            );
            metrics.caseCreated(type, severity);
        } catch (DataIntegrityViolationException duplicate) {
            writer.reobserve(
                    transfer.getTransferId(), type, severity, transfer.getStatus(),
                    transfer.getVersion(), ledgerCount
            );
            metrics.caseReobserved(type);
        }
    }

    public void acknowledge(UUID caseId) {
        writer.acknowledge(caseId);
    }

    public void resolve(UUID caseId, String note) {
        writer.resolve(caseId, note);
    }
}
