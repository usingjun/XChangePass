package bumblebee.xchangepass.domain.monitoring;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEventType;
import bumblebee.xchangepass.domain.monitoring.repository.TransactionStatusEventRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QueryDSLConfig.class)
class TransactionStatusEventDatasetFactoryTest {

    @Autowired
    private TransactionStatusEventRepository eventRepository;

    @Test
    void createsDeterministicBenchmarkDataset() {
        int eventCount = TransactionStatusEventDatasetFactory.DEFAULT_EVENT_COUNT;
        int chunkSize = TransactionStatusEventDatasetFactory.DEFAULT_CHUNK_SIZE;
        for (int offset = 0; offset < eventCount; offset += chunkSize) {
            eventRepository.saveAll(TransactionStatusEventDatasetFactory.createChunk(
                    offset,
                    Math.min(chunkSize, eventCount - offset),
                    TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                    TransactionStatusEventDatasetFactory.DEFAULT_END_AT,
                    eventCount
            ));
            eventRepository.flush();
        }

        var events = eventRepository.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                TransactionStatusEventDatasetFactory.DEFAULT_END_AT
        );

        assertThat(events).hasSize(eventCount);
        assertThat(events.get(0).getOccurredAt())
                .isEqualTo(TransactionStatusEventDatasetFactory.DEFAULT_START_AT);
        assertThat(events.get(events.size() - 1).getOccurredAt())
                .isBefore(TransactionStatusEventDatasetFactory.DEFAULT_END_AT);
        assertThat(events)
                .extracting(event -> event.getEventType())
                .containsAll(EnumSet.allOf(TransactionStatusEventType.class));
        assertThat(events)
                .anySatisfy(event -> assertThat(event.getRetryable()).isTrue())
                .anySatisfy(event -> assertThat(event.getRetryable()).isFalse())
                .anySatisfy(event -> assertThat(event.getRetryable()).isNull());
        assertThat(events)
                .anySatisfy(event -> assertThat(event.getErrorCode()).isEqualTo("FRAUD_DETECTION_UNAVAILABLE"))
                .anySatisfy(event -> assertThat(event.getErrorCode()).isEqualTo("TRANSACTION_PROCESSING_FAILED"))
                .anySatisfy(event -> assertThat(event.getErrorCode()).isEqualTo("TRANSACTION_STALE"));
    }

    @Test
    void keepsMillionScaleEventsInsideBenchmarkRange() {
        int eventCount = 1_000_000;

        var first = TransactionStatusEventDatasetFactory.createChunk(
                0,
                1,
                TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                TransactionStatusEventDatasetFactory.DEFAULT_END_AT,
                eventCount
        ).get(0);
        var last = TransactionStatusEventDatasetFactory.createChunk(
                eventCount - 1,
                1,
                TransactionStatusEventDatasetFactory.DEFAULT_START_AT,
                TransactionStatusEventDatasetFactory.DEFAULT_END_AT,
                eventCount
        ).get(0);

        assertThat(first.getOccurredAt())
                .isEqualTo(TransactionStatusEventDatasetFactory.DEFAULT_START_AT);
        assertThat(last.getOccurredAt())
                .isBefore(TransactionStatusEventDatasetFactory.DEFAULT_END_AT);
    }
}
