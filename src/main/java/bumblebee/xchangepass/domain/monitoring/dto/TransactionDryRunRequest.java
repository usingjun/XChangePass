package bumblebee.xchangepass.domain.monitoring.dto;

public record TransactionDryRunRequest(
        Long adminId,
        String reason
) {
}
