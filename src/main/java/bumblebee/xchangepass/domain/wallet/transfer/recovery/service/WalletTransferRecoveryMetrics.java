package bumblebee.xchangepass.domain.wallet.transfer.recovery.service;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WalletTransferRecoveryMetrics {

    private final MeterRegistry registry;

    public WalletTransferRecoveryMetrics(MeterRegistry registry,
                                         WalletTransferRecoveryCaseRepository caseRepository) {
        this.registry = registry;
        for (WalletTransferRecoveryCaseType type : WalletTransferRecoveryCaseType.values()) {
            for (WalletTransferRecoveryCaseSeverity severity : WalletTransferRecoveryCaseSeverity.values()) {
                Gauge.builder("wallet.transfer.recovery.open.cases", caseRepository,
                                repository -> repository.countByCaseStatusNotAndCaseTypeAndSeverity(
                                        WalletTransferRecoveryCaseStatus.RESOLVED, type, severity
                                ))
                        .tag("type", type.name())
                        .tag("severity", severity.name())
                        .register(registry);
            }
        }
    }

    public void finalized(WalletTransferStatus previousStatus) {
        registry.counter("wallet.transfer.recovery.finalized",
                "previous_status", previousStatus.name()).increment();
    }

    public void conflict() {
        registry.counter("wallet.transfer.recovery.conflicts").increment();
    }

    public void caseCreated(WalletTransferRecoveryCaseType type,
                            WalletTransferRecoveryCaseSeverity severity) {
        registry.counter("wallet.transfer.recovery.cases.created",
                "type", type.name(), "severity", severity.name()).increment();
    }

    public void caseReobserved(WalletTransferRecoveryCaseType type) {
        registry.counter("wallet.transfer.recovery.cases.reobserved",
                "type", type.name()).increment();
    }

    public void error(String operation) {
        registry.counter("wallet.transfer.recovery.errors", "operation", operation).increment();
    }
}
