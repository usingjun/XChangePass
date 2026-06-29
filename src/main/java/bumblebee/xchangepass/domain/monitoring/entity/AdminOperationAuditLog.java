package bumblebee.xchangepass.domain.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "admin_operation_audit_log")
public class AdminOperationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 60)
    private AdminOperationActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private AdminOperationTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false, length = 80)
    private String targetId;

    @Column(name = "before_status", updatable = false, length = 80)
    private String beforeStatus;

    @Column(name = "after_status", updatable = false, length = 80)
    private String afterStatus;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "request_payload", updatable = false, columnDefinition = "text")
    private String requestPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 20)
    private AdminOperationResult result;

    @Column(name = "error_message", updatable = false, length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminOperationAuditLog() {
    }

    public AdminOperationAuditLog(Long adminId,
                                  AdminOperationActionType actionType,
                                  AdminOperationTargetType targetType,
                                  String targetId,
                                  String beforeStatus,
                                  String afterStatus,
                                  String reason,
                                  String requestPayload,
                                  AdminOperationResult result,
                                  String errorMessage) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }
        if (actionType == null || targetType == null || result == null) {
            throw new IllegalArgumentException("actionType, targetType, and result are required");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId is required");
        }
        this.adminId = adminId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeStatus = truncate(beforeStatus, 80);
        this.afterStatus = truncate(afterStatus, 80);
        this.reason = truncate(reason, 500);
        this.requestPayload = requestPayload;
        this.result = result;
        this.errorMessage = truncate(errorMessage, 1000);
        this.createdAt = LocalDateTime.now();
    }

    private String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(maxLength, value.length()));
    }
}
