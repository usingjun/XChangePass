package bumblebee.xchangepass.domain.monitoring.controller;

import bumblebee.xchangepass.domain.monitoring.dto.TransactionDryRunRequest;
import bumblebee.xchangepass.domain.monitoring.dto.TransactionDryRunResponse;
import bumblebee.xchangepass.domain.monitoring.service.TransactionOperationSafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/operations")
public class AdminTransactionOperationController {

    private final TransactionOperationSafetyService transactionOperationSafetyService;

    @PostMapping("/transactions/{transactionId}/dry-run")
    public TransactionDryRunResponse dryRun(@PathVariable UUID transactionId,
                                            @RequestBody TransactionDryRunRequest request) {
        return transactionOperationSafetyService.dryRun(transactionId, request.adminId(), request.reason());
    }
}
