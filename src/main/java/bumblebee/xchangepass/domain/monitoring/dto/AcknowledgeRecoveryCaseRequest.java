package bumblebee.xchangepass.domain.monitoring.dto;

public record AcknowledgeRecoveryCaseRequest(
        Long adminId,
        String reason
) {
}
