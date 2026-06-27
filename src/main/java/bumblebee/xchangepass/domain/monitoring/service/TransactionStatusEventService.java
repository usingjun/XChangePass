package bumblebee.xchangepass.domain.monitoring.service;

import bumblebee.xchangepass.domain.monitoring.dto.TransactionTimelineEventResponse;
import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionStatusEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStatusEventService {

    private final TransactionStatusEventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;

    public void record(TransactionStatusEvent event) {
        eventRepository.save(event);
    }

    public void recordRequiresNew(TransactionStatusEvent event) {
        newRequiresNewTemplate().executeWithoutResult(status -> eventRepository.save(event));
    }

    public void recordBestEffort(TransactionStatusEvent event) {
        try {
            recordRequiresNew(event);
        } catch (RuntimeException exception) {
            log.warn("Failed to record transaction status event: transactionId={}, eventType={}",
                    event.getTransactionId(), event.getEventType(), exception);
        }
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        return transactionTemplate;
    }

    @Transactional(readOnly = true)
    public List<TransactionTimelineEventResponse> findTimeline(UUID transactionId) {
        return eventRepository.findByTransactionIdOrderByOccurredAtAscIdAsc(transactionId).stream()
                .map(TransactionTimelineEventResponse::from)
                .toList();
    }
}
