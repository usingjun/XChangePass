package bumblebee.xchangepass.domain.monitoring.service;

import bumblebee.xchangepass.domain.monitoring.dto.RecoveryCaseOperationResponse;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationActionType;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecoveryCaseOperationService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final WalletTransferRecoveryCaseRepository recoveryCaseRepository;
    private final AdminOperationAuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RecoveryCaseOperationResponse> findRecoveryCases(
            WalletTransferRecoveryCaseType caseType,
            WalletTransferRecoveryCaseSeverity severity,
            WalletTransferRecoveryCaseStatus caseStatus,
            UUID transactionId,
            LocalDateTime fromDetectedAt,
            LocalDateTime toDetectedAt,
            Integer limit
    ) {
        validateRange(fromDetectedAt, toDetectedAt);
        return recoveryCaseRepository.findForOperations(
                        caseType,
                        severity,
                        caseStatus,
                        transactionId,
                        fromDetectedAt,
                        toDetectedAt,
                        PageRequest.of(0, normalizeLimit(limit))
                )
                .stream()
                .map(RecoveryCaseOperationResponse::from)
                .toList();
    }

    @Transactional
    public RecoveryCaseOperationResponse acknowledge(UUID caseId, Long adminId, String reason) {
        validateAdminId(adminId);
        WalletTransferRecoveryCase recoveryCase = findCaseForUpdate(caseId);
        String beforeStatus = recoveryCase.getCaseStatus().name();
        try {
            recoveryCase.acknowledge();
            String afterStatus = recoveryCase.getCaseStatus().name();
            auditLogService.recordSuccess(
                    adminId,
                    AdminOperationActionType.ACKNOWLEDGE_RECOVERY_CASE,
                    AdminOperationTargetType.RECOVERY_CASE,
                    caseId.toString(),
                    beforeStatus,
                    afterStatus,
                    reason,
                    null
            );
            return RecoveryCaseOperationResponse.from(recoveryCase);
        } catch (RuntimeException exception) {
            auditLogService.recordBlocked(
                    adminId,
                    AdminOperationActionType.ACKNOWLEDGE_RECOVERY_CASE,
                    AdminOperationTargetType.RECOVERY_CASE,
                    caseId.toString(),
                    beforeStatus,
                    reason,
                    null,
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @Transactional
    public RecoveryCaseOperationResponse resolve(UUID caseId, Long adminId, String note) {
        validateAdminId(adminId);
        WalletTransferRecoveryCase recoveryCase = findCaseForUpdate(caseId);
        String beforeStatus = recoveryCase.getCaseStatus().name();
        try {
            String normalizedNote = normalizeResolutionNote(note);
            recoveryCase.resolve(normalizedNote);
            String afterStatus = recoveryCase.getCaseStatus().name();
            auditLogService.recordSuccess(
                    adminId,
                    AdminOperationActionType.RESOLVE_RECOVERY_CASE,
                    AdminOperationTargetType.RECOVERY_CASE,
                    caseId.toString(),
                    beforeStatus,
                    afterStatus,
                    normalizedNote,
                    "{\"note\":\"" + normalizedNote + "\"}"
            );
            return RecoveryCaseOperationResponse.from(recoveryCase);
        } catch (RuntimeException exception) {
            auditLogService.recordBlocked(
                    adminId,
                    AdminOperationActionType.RESOLVE_RECOVERY_CASE,
                    AdminOperationTargetType.RECOVERY_CASE,
                    caseId.toString(),
                    beforeStatus,
                    note,
                    note == null ? null : "{\"note\":\"" + note + "\"}",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void validateRange(LocalDateTime fromDetectedAt, LocalDateTime toDetectedAt) {
        if (fromDetectedAt != null && toDetectedAt != null && !fromDetectedAt.isBefore(toDetectedAt)) {
            throw new IllegalArgumentException("fromDetectedAt must be before toDetectedAt");
        }
    }

    private WalletTransferRecoveryCase findCaseForUpdate(UUID caseId) {
        return recoveryCaseRepository.findByIdForOperation(caseId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
    }

    private void validateAdminId(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
    }

    private String normalizeResolutionNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("resolution note is required");
        }
        return note.strip();
    }
}
