package bumblebee.xchangepass.domain.monitoring.service;

import bumblebee.xchangepass.domain.monitoring.dto.AdminTransactionRecommendedAction;
import bumblebee.xchangepass.domain.monitoring.dto.TransactionDryRunResponse;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationActionType;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionOperationSafetyService {

    private final WalletTransferRepository walletTransferRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AdminOperationAuditLogService auditLogService;

    @Transactional
    public TransactionDryRunResponse dryRun(UUID transactionId, Long adminId, String reason) {
        validateAdminId(adminId);
        WalletTransfer transfer = walletTransferRepository.findById(transactionId)
                .orElseThrow(ErrorCode.TRANSACTION_PROCESSING_FAILED::commonException);
        long ledgerCount = walletTransactionRepository.countByTransferId(transactionId);
        TransactionDryRunDecision decision = decide(transfer.getStatus(), ledgerCount);

        auditLogService.recordSuccess(
                adminId,
                AdminOperationActionType.DRY_RUN_TRANSACTION,
                AdminOperationTargetType.TRANSACTION,
                transactionId.toString(),
                transfer.getStatus().name(),
                transfer.getStatus().name(),
                reason,
                "{\"reason\":\"" + safe(reason) + "\"}"
        );

        return new TransactionDryRunResponse(
                transactionId,
                transfer.getStatus(),
                transfer.getFailureStage(),
                transfer.getRetryable(),
                ledgerCount > 0,
                ledgerCount,
                decision.canAutoFail(),
                decision.recommendedAction(),
                decision.reason()
        );
    }

    private TransactionDryRunDecision decide(WalletTransferStatus status, long ledgerCount) {
        if (ledgerCount > 0) {
            return new TransactionDryRunDecision(
                    false,
                    AdminTransactionRecommendedAction.KEEP_OPERATIONAL_EXCEPTION,
                    "원장이 존재하므로 상태만 실패로 변경하면 정합성이 깨질 수 있습니다."
            );
        }
        if (status == WalletTransferStatus.REQUESTED || status == WalletTransferStatus.VALIDATING) {
            return new TransactionDryRunDecision(
                    true,
                    AdminTransactionRecommendedAction.MARK_FAILED_ALLOWED,
                    "원장이 없고 자금 처리 전 상태이므로 수동 실패 처리가 가능합니다."
            );
        }
        if (status == WalletTransferStatus.PROCESSING) {
            return new TransactionDryRunDecision(
                    false,
                    AdminTransactionRecommendedAction.KEEP_OPERATIONAL_EXCEPTION,
                    "자금 처리 단계에 진입했으므로 운영 예외로 유지하고 추가 확인이 필요합니다."
            );
        }
        return new TransactionDryRunDecision(
                false,
                AdminTransactionRecommendedAction.NO_ACTION,
                "이미 종료된 거래이므로 추가 조치가 필요하지 않습니다."
        );
    }

    private void validateAdminId(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private record TransactionDryRunDecision(
            boolean canAutoFail,
            AdminTransactionRecommendedAction recommendedAction,
            String reason
    ) {
    }
}
