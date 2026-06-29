package bumblebee.xchangepass.domain.monitoring.controller;

import bumblebee.xchangepass.domain.monitoring.dto.AcknowledgeRecoveryCaseRequest;
import bumblebee.xchangepass.domain.monitoring.dto.RecoveryCaseOperationResponse;
import bumblebee.xchangepass.domain.monitoring.dto.ResolveRecoveryCaseOperationRequest;
import bumblebee.xchangepass.domain.monitoring.service.RecoveryCaseOperationService;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/operations")
public class AdminRecoveryCaseController {

    private final RecoveryCaseOperationService recoveryCaseOperationService;

    @GetMapping("/recovery-cases")
    public List<RecoveryCaseOperationResponse> findRecoveryCases(
            @RequestParam(required = false) WalletTransferRecoveryCaseType caseType,
            @RequestParam(required = false) WalletTransferRecoveryCaseSeverity severity,
            @RequestParam(required = false) WalletTransferRecoveryCaseStatus caseStatus,
            @RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDetectedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toDetectedAt,
            @RequestParam(required = false) Integer limit) {
        return recoveryCaseOperationService.findRecoveryCases(
                caseType,
                severity,
                caseStatus,
                transactionId,
                fromDetectedAt,
                toDetectedAt,
                limit
        );
    }

    @PostMapping("/recovery-cases/{caseId}/acknowledge")
    public RecoveryCaseOperationResponse acknowledge(@PathVariable UUID caseId,
                                                     @RequestBody AcknowledgeRecoveryCaseRequest request) {
        return recoveryCaseOperationService.acknowledge(caseId, request.adminId(), request.reason());
    }

    @PostMapping("/recovery-cases/{caseId}/resolve")
    public RecoveryCaseOperationResponse resolve(@PathVariable UUID caseId,
                                                 @RequestBody ResolveRecoveryCaseOperationRequest request) {
        return recoveryCaseOperationService.resolve(caseId, request.adminId(), request.note());
    }
}
