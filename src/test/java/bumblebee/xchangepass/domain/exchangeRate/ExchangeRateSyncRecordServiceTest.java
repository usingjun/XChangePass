package bumblebee.xchangepass.domain.exchangeRate;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailure;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncHistory;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshotStatus;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncStatus;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncFailureRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncHistoryRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncSnapshotRepository;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateSyncRecordService;
import bumblebee.xchangepass.domain.exchangeRate.service.ExchangeRateValidationResult;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDSLConfig.class,
        ExchangeRateSyncRecordService.class,
        ExchangeRateSyncRecordServiceTest.ObjectMapperTestConfig.class
})
class ExchangeRateSyncRecordServiceTest {

    @jakarta.annotation.Resource
    private ExchangeRateSyncHistoryRepository historyRepository;

    @jakarta.annotation.Resource
    private ExchangeRateSyncSnapshotRepository snapshotRepository;

    @jakarta.annotation.Resource
    private ExchangeRateSyncFailureRepository failureRepository;

    @jakarta.annotation.Resource
    private ExchangeRateSyncRecordService recordService;

    @Test
    void startCreatesRunningHistory() {
        ExchangeRateSyncHistory history = recordService.start(4);

        ExchangeRateSyncHistory saved = historyRepository.findById(history.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncStatus.RUNNING);
        assertThat(saved.getRequestedBaseCurrencyCount()).isEqualTo(4);
        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void startRejectsInvalidRequestedCount() {
        assertThatThrownBy(() -> recordService.start(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestedBaseCurrencyCount");
    }

    @Test
    void recordsReceivedSnapshotWithPayloadHash() {
        ExchangeRateSyncHistory history = recordService.start(1);
        var snapshot = recordService.recordReceivedSnapshot(
                history,
                "USD",
                response("USD")
        );

        var saved = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncSnapshotStatus.RECEIVED);
        assertThat(saved.getBaseCurrency()).isEqualTo("USD");
        assertThat(saved.getPayload()).contains("\"base_code\":\"USD\"");
        assertThat(saved.getPayloadHash()).hasSize(64);
        assertThat(saved.getSyncHistory().getId()).isEqualTo(history.getId());
    }

    @Test
    void recordsFailedSnapshot() {
        ExchangeRateSyncHistory history = recordService.start(1);
        var snapshot = recordService.recordFailedSnapshot(history, "JPY", "timeout");

        var saved = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncSnapshotStatus.FAILED);
        assertThat(saved.getBaseCurrency()).isEqualTo("JPY");
        assertThat(saved.getPayload()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("timeout");
    }

    @Test
    void recordsFailure() {
        ExchangeRateSyncHistory history = recordService.start(1);

        ExchangeRateSyncFailure failure = recordService.recordFailure(
                history,
                "USD",
                ExchangeRateSyncFailureType.API_ERROR,
                null,
                "quota exceeded",
                true
        );

        assertThat(failureRepository.findById(failure.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getFailureType()).isEqualTo(ExchangeRateSyncFailureType.API_ERROR);
                    assertThat(saved.getBaseCurrency()).isEqualTo("USD");
                    assertThat(saved.isRetryable()).isTrue();
                    assertThat(saved.isResolved()).isFalse();
                });
    }

    @Test
    void recordsValidationFailure() {
        ExchangeRateSyncHistory history = recordService.start(1);

        ExchangeRateSyncFailure failure = recordService.recordValidationFailure(
                history,
                "USD",
                "{}",
                ExchangeRateValidationResult.invalid(
                        ExchangeRateSyncFailureType.MISSING_REQUIRED_CURRENCY,
                        "Required currency is missing: KRW"
                )
        );

        assertThat(failureRepository.findById(failure.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getFailureType())
                            .isEqualTo(ExchangeRateSyncFailureType.MISSING_REQUIRED_CURRENCY);
                    assertThat(saved.getRawPayload()).isEqualTo("{}");
                    assertThat(saved.isRetryable()).isTrue();
                });
    }

    @Test
    void completeUpdatesHistory() {
        ExchangeRateSyncHistory history = recordService.start(4);

        recordService.complete(history, 4, true);

        ExchangeRateSyncHistory saved = historyRepository.findById(history.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncStatus.COMPLETED);
        assertThat(saved.getSuccessCount()).isEqualTo(4);
        assertThat(saved.getFailureCount()).isZero();
        assertThat(saved.isActivated()).isTrue();
        assertThat(saved.getFinishedAt()).isNotNull();
    }

    @Test
    void partialFailUpdatesHistory() {
        ExchangeRateSyncHistory history = recordService.start(4);

        recordService.partialFail(history, 3, 1, "JPY failed");

        ExchangeRateSyncHistory saved = historyRepository.findById(history.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncStatus.PARTIAL_FAILED);
        assertThat(saved.getSuccessCount()).isEqualTo(3);
        assertThat(saved.getFailureCount()).isEqualTo(1);
        assertThat(saved.isActivated()).isFalse();
        assertThat(saved.getErrorMessage()).isEqualTo("JPY failed");
    }

    @Test
    void failUpdatesHistory() {
        ExchangeRateSyncHistory history = recordService.start(4);

        recordService.fail(history, "all failed");

        ExchangeRateSyncHistory saved = historyRepository.findById(history.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncStatus.FAILED);
        assertThat(saved.getSuccessCount()).isZero();
        assertThat(saved.getFailureCount()).isEqualTo(4);
        assertThat(saved.isActivated()).isFalse();
        assertThat(saved.getErrorMessage()).isEqualTo("all failed");
    }

    private ExchangeRateResponse response(String baseCurrency) {
        return ExchangeRateResponse.builder()
                .baseCurrency(baseCurrency)
                .conversionRates(Map.of(
                        "KRW", 1390.1,
                        "USD", 1.0,
                        "JPY", 157.2,
                        "EUR", 0.92
                ))
                .build();
    }

    @TestConfiguration
    static class ObjectMapperTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
