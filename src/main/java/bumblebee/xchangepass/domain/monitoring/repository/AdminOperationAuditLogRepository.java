package bumblebee.xchangepass.domain.monitoring.repository;

import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationAuditLog;
import bumblebee.xchangepass.domain.monitoring.entity.AdminOperationTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminOperationAuditLogRepository
        extends JpaRepository<AdminOperationAuditLog, Long> {

    List<AdminOperationAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    List<AdminOperationAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            AdminOperationTargetType targetType,
            String targetId
    );
}
