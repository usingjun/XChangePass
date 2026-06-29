package bumblebee.xchangepass.domain.monitoring.service;

import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationActionType;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationAuditLog;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationResult;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import bumblebee.xchangepass.domain.monitoring.repository.AdminOperationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOperationAuditLogService {

    private final AdminOperationAuditLogRepository auditLogRepository;

    @Transactional
    public AdminOperationAuditLog recordSuccess(Long adminId,
                                                AdminOperationActionType actionType,
                                                AdminOperationTargetType targetType,
                                                String targetId,
                                                String beforeStatus,
                                                String afterStatus,
                                                String reason,
                                                String requestPayload) {
        return record(
                adminId,
                actionType,
                targetType,
                targetId,
                beforeStatus,
                afterStatus,
                reason,
                requestPayload,
                AdminOperationResult.SUCCESS,
                null
        );
    }

    @Transactional
    public AdminOperationAuditLog recordFailure(Long adminId,
                                                AdminOperationActionType actionType,
                                                AdminOperationTargetType targetType,
                                                String targetId,
                                                String beforeStatus,
                                                String afterStatus,
                                                String reason,
                                                String requestPayload,
                                                String errorMessage) {
        return record(
                adminId,
                actionType,
                targetType,
                targetId,
                beforeStatus,
                afterStatus,
                reason,
                requestPayload,
                AdminOperationResult.FAILED,
                errorMessage
        );
    }

    @Transactional
    public AdminOperationAuditLog recordBlocked(Long adminId,
                                                AdminOperationActionType actionType,
                                                AdminOperationTargetType targetType,
                                                String targetId,
                                                String beforeStatus,
                                                String reason,
                                                String requestPayload,
                                                String errorMessage) {
        return record(
                adminId,
                actionType,
                targetType,
                targetId,
                beforeStatus,
                beforeStatus,
                reason,
                requestPayload,
                AdminOperationResult.BLOCKED,
                errorMessage
        );
    }

    private AdminOperationAuditLog record(Long adminId,
                                          AdminOperationActionType actionType,
                                          AdminOperationTargetType targetType,
                                          String targetId,
                                          String beforeStatus,
                                          String afterStatus,
                                          String reason,
                                          String requestPayload,
                                          AdminOperationResult result,
                                          String errorMessage) {
        return auditLogRepository.save(new AdminOperationAuditLog(
                adminId,
                actionType,
                targetType,
                targetId,
                beforeStatus,
                afterStatus,
                reason,
                requestPayload,
                result,
                errorMessage
        ));
    }
}
