package bumblebee.xchangepass.domain.monitoring.controller;

import bumblebee.xchangepass.domain.monitoring.dto.TransactionTimelineEventResponse;
import bumblebee.xchangepass.domain.monitoring.service.TransactionStatusEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/transactions")
public class TransactionMonitoringController {

    private final TransactionStatusEventService eventService;

    @GetMapping("/{transactionId}/events")
    @ResponseStatus(HttpStatus.OK)
    public List<TransactionTimelineEventResponse> events(@PathVariable UUID transactionId) {
        return eventService.findTimeline(transactionId);
    }
}
