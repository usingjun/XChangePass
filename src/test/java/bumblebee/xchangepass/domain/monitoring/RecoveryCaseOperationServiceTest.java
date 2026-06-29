package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.service.RecoveryCaseOperationService;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationAuditLog;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationResult;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.monitoring.repository.AdminOperationAuditLogRepository;
import bumblebee.xchangepass.domain.monitoring.service.AdminOperationAuditLogService;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({RecoveryCaseOperationService.class, AdminOperationAuditLogService.class, QueryDSLConfig.class})
class RecoveryCaseOperationServiceTest {

    @Autowired
    private WalletTransferRecoveryCaseRepository recoveryCaseRepository;

    @Autowired
    private AdminOperationAuditLogRepository auditLogRepository;

    @Autowired
    private RecoveryCaseOperationService operationService;

    @Test
    void findsRecoveryCasesByFilters() {
        UUID matchingTransferId = UUID.randomUUID();
        recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                matchingTransferId,
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                3L,
                2
        ));
        recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.PROCESSING,
                1L,
                0
        ));

        var responses = operationService.findRecoveryCases(
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferRecoveryCaseStatus.OPEN,
                matchingTransferId,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                100
        );

        assertThat(responses).singleElement()
                .satisfies(response -> {
                    assertThat(response.transactionId()).isEqualTo(matchingTransferId);
                    assertThat(response.caseType()).isEqualTo(WalletTransferRecoveryCaseType.LEDGER_MISMATCH);
                    assertThat(response.severity()).isEqualTo(WalletTransferRecoveryCaseSeverity.CRITICAL);
                    assertThat(response.caseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.OPEN);
                    assertThat(response.observedLedgerCount()).isEqualTo(2);
                    assertThat(response.detectionCount()).isEqualTo(1);
                });
    }

    @Test
    void appliesLimit() {
        recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                1L,
                1
        ));
        recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.VALIDATING,
                1L,
                0
        ));

        var responses = operationService.findRecoveryCases(
                null, null, null, null, null, null, 1
        );

        assertThat(responses).hasSize(1);
    }

    @Test
    void rejectsInvalidLimit() {
        assertThatThrownBy(() -> operationService.findRecoveryCases(
                null, null, null, null, null, null, 0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void rejectsInvalidDetectedAtRange() {
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> operationService.findRecoveryCases(
                null, null, null, null, now, now, 100
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDetectedAt");
    }

    @Test
    void acknowledgesOpenRecoveryCaseAndRecordsAuditLog() {
        WalletTransferRecoveryCase recoveryCase = recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                1L,
                1
        ));

        var response = operationService.acknowledge(recoveryCase.getCaseId(), 10L, "start review");

        assertThat(response.caseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.ACKNOWLEDGED);
        assertThat(recoveryCaseRepository.findById(recoveryCase.getCaseId()))
                .get()
                .extracting(WalletTransferRecoveryCase::getCaseStatus)
                .isEqualTo(WalletTransferRecoveryCaseStatus.ACKNOWLEDGED);
        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getResult()).isEqualTo(AdminOperationResult.SUCCESS);
                    assertThat(log.getBeforeStatus()).isEqualTo("OPEN");
                    assertThat(log.getAfterStatus()).isEqualTo("ACKNOWLEDGED");
                    assertThat(log.getAdminId()).isEqualTo(10L);
                });
    }

    @Test
    void blocksDuplicateAcknowledgeAndRecordsAuditLog() {
        WalletTransferRecoveryCase recoveryCase = new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                1L,
                1
        );
        recoveryCase.acknowledge();
        recoveryCaseRepository.save(recoveryCase);

        assertThatThrownBy(() -> operationService.acknowledge(recoveryCase.getCaseId(), 10L, "again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an open recovery case");

        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getResult()).isEqualTo(AdminOperationResult.BLOCKED);
                    assertThat(log.getBeforeStatus()).isEqualTo("ACKNOWLEDGED");
                    assertThat(log.getAfterStatus()).isEqualTo("ACKNOWLEDGED");
                });
    }

    @Test
    void resolvesOpenRecoveryCaseAndRecordsAuditLog() {
        WalletTransferRecoveryCase recoveryCase = recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.VALIDATING,
                1L,
                0
        ));

        var response = operationService.resolve(recoveryCase.getCaseId(), 11L, "ledger checked");

        assertThat(response.caseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.RESOLVED);
        assertThat(response.resolutionNote()).isEqualTo("ledger checked");
        assertThat(response.resolvedAt()).isNotNull();
        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getResult()).isEqualTo(AdminOperationResult.SUCCESS);
                    assertThat(log.getBeforeStatus()).isEqualTo("OPEN");
                    assertThat(log.getAfterStatus()).isEqualTo("RESOLVED");
                    assertThat(log.getReason()).isEqualTo("ledger checked");
                });
    }

    @Test
    void resolvesAcknowledgedRecoveryCase() {
        WalletTransferRecoveryCase recoveryCase = new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.PROCESSING,
                1L,
                0
        );
        recoveryCase.acknowledge();
        recoveryCaseRepository.save(recoveryCase);

        var response = operationService.resolve(recoveryCase.getCaseId(), 11L, "done");

        assertThat(response.caseStatus()).isEqualTo(WalletTransferRecoveryCaseStatus.RESOLVED);
        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getResult()).isEqualTo(AdminOperationResult.SUCCESS);
                    assertThat(log.getBeforeStatus()).isEqualTo("ACKNOWLEDGED");
                    assertThat(log.getAfterStatus()).isEqualTo("RESOLVED");
                });
    }

    @Test
    void blocksAlreadyResolvedRecoveryCaseAndRecordsAuditLog() {
        WalletTransferRecoveryCase recoveryCase = new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.PROCESSING,
                1L,
                0
        );
        recoveryCase.resolve("already done");
        recoveryCaseRepository.save(recoveryCase);

        assertThatThrownBy(() -> operationService.resolve(recoveryCase.getCaseId(), 11L, "again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already resolved");

        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getResult()).isEqualTo(AdminOperationResult.BLOCKED);
                    assertThat(log.getBeforeStatus()).isEqualTo("RESOLVED");
                    assertThat(log.getAfterStatus()).isEqualTo("RESOLVED");
                });
    }

    @Test
    void blocksBlankResolveNoteAndRecordsAuditLog() {
        WalletTransferRecoveryCase recoveryCase = recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                WalletTransferRecoveryCaseSeverity.WARNING,
                WalletTransferStatus.PROCESSING,
                1L,
                0
        ));

        assertThatThrownBy(() -> operationService.resolve(recoveryCase.getCaseId(), 11L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolution note");

        assertThat(auditLogsFor(recoveryCase))
                .singleElement()
                .extracting(AdminOperationAuditLog::getResult)
                .isEqualTo(AdminOperationResult.BLOCKED);
    }

    @Test
    void rejectsNullAdminId() {
        WalletTransferRecoveryCase recoveryCase = recoveryCaseRepository.save(new WalletTransferRecoveryCase(
                UUID.randomUUID(),
                WalletTransferRecoveryCaseType.LEDGER_MISMATCH,
                WalletTransferRecoveryCaseSeverity.CRITICAL,
                WalletTransferStatus.PROCESSING,
                1L,
                1
        ));

        assertThatThrownBy(() -> operationService.acknowledge(recoveryCase.getCaseId(), null, "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminId");
    }

    private java.util.List<AdminOperationAuditLog> auditLogsFor(WalletTransferRecoveryCase recoveryCase) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                AdminOperationTargetType.RECOVERY_CASE,
                recoveryCase.getCaseId().toString()
        );
    }
}
