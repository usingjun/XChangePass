package bumblebee.xchangepass.domain.monitoring.dto;

public record ResolveRecoveryCaseOperationRequest(
        Long adminId,
        String note
) {
}
