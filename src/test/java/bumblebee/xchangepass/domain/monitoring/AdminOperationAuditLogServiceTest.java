package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationActionType;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationAuditLog;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationResult;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.monitoring.repository.AdminOperationAuditLogRepository;
import bumblebee.xchangepass.domain.monitoring.service.AdminOperationAuditLogService;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AdminOperationAuditLogService.class, QueryDSLConfig.class})
class AdminOperationAuditLogServiceTest {

    @Autowired
    private AdminOperationAuditLogRepository auditLogRepository;

    @Autowired
    private AdminOperationAuditLogService auditLogService;

    @Test
    void recordsSuccessAuditLog() {
        AdminOperationAuditLog log = auditLogService.recordSuccess(
                1L,
                AdminOperationActionType.RESOLVE_RECOVERY_CASE,
                AdminOperationTargetType.RECOVERY_CASE,
                "case-1",
                "ACKNOWLEDGED",
                "RESOLVED",
                "manual review completed",
                "{\"note\":\"ok\"}"
        );

        AdminOperationAuditLog saved = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(saved.getAdminId()).isEqualTo(1L);
        assertThat(saved.getActionType()).isEqualTo(AdminOperationActionType.RESOLVE_RECOVERY_CASE);
        assertThat(saved.getTargetType()).isEqualTo(AdminOperationTargetType.RECOVERY_CASE);
        assertThat(saved.getTargetId()).isEqualTo("case-1");
        assertThat(saved.getBeforeStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(saved.getAfterStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResult()).isEqualTo(AdminOperationResult.SUCCESS);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsFailureAuditLog() {
        AdminOperationAuditLog log = auditLogService.recordFailure(
                2L,
                AdminOperationActionType.RETRY_EXCHANGE_RATE_SYNC_FAILURE,
                AdminOperationTargetType.EXCHANGE_RATE_SYNC_FAILURE,
                "failure-1",
                "OPEN",
                "OPEN",
                "retry requested",
                "{}",
                "provider timeout"
        );

        AdminOperationAuditLog saved = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(saved.getResult()).isEqualTo(AdminOperationResult.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void recordsBlockedAuditLogWithoutChangingStatus() {
        AdminOperationAuditLog log = auditLogService.recordBlocked(
                3L,
                AdminOperationActionType.MARK_TRANSACTION_FAILED,
                AdminOperationTargetType.TRANSACTION,
                "tx-1",
                "COMPLETED",
                "completed transaction cannot be failed",
                "{}",
                "already completed"
        );

        AdminOperationAuditLog saved = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(saved.getResult()).isEqualTo(AdminOperationResult.BLOCKED);
        assertThat(saved.getBeforeStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getAfterStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void findsAuditLogsByAdminIdAndTarget() {
        auditLogService.recordSuccess(
                1L,
                AdminOperationActionType.DRY_RUN_TRANSACTION,
                AdminOperationTargetType.TRANSACTION,
                "tx-1",
                "VALIDATING",
                "VALIDATING",
                "check",
                "{}"
        );
        auditLogService.recordFailure(
                2L,
                AdminOperationActionType.DRY_RUN_TRANSACTION,
                AdminOperationTargetType.TRANSACTION,
                "tx-1",
                "VALIDATING",
                "VALIDATING",
                "check",
                "{}",
                "failed"
        );

        assertThat(auditLogRepository.findByAdminIdOrderByCreatedAtDesc(1L))
                .extracting(AdminOperationAuditLog::getAdminId)
                .containsExactly(1L);
        assertThat(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                AdminOperationTargetType.TRANSACTION,
                "tx-1"
        )).hasSize(2);
    }

    @Test
    void truncatesLongErrorMessage() {
        String longError = "x".repeat(1200);

        AdminOperationAuditLog log = auditLogService.recordFailure(
                1L,
                AdminOperationActionType.MARK_TRANSACTION_FAILED,
                AdminOperationTargetType.TRANSACTION,
                "tx-1",
                "PROCESSING",
                "PROCESSING",
                "manual fail",
                "{}",
                longError
        );

        AdminOperationAuditLog saved = auditLogRepository.findById(log.getId()).orElseThrow();
        assertThat(saved.getErrorMessage()).hasSize(1000);
    }
}
