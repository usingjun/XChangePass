package bumblebee.xchangepass.domain.monitoring.controller;

import bumblebee.xchangepass.domain.monitoring.dto.TransactionStatusHourlySummaryResponse;
import bumblebee.xchangepass.domain.monitoring.dto.TransactionStatusHourlySummaryResult;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusHourlySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/monitoring/transactions")
public class TransactionMonitoringBatchController {

    private final TransactionStatusHourlySummaryService summaryService;

    @PostMapping("/hourly-summary")
    @ResponseStatus(HttpStatus.OK)
    public TransactionStatusHourlySummaryResult summarizeHourly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return summaryService.summarize(from, to);
    }

    @GetMapping("/hourly-summary")
    public List<TransactionStatusHourlySummaryResponse> findHourlySummaries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return summaryService.findSummaries(from, to);
    }
}
