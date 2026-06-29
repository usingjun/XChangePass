package bumblebee.xchangepass.domain.exchangeRate;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailure;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncHistory;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshot;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshotStatus;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncStatus;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncFailureRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncHistoryRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncSnapshotRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QueryDSLConfig.class)
class ExchangeRateSyncRepositoryTest {

    @Autowired
    private ExchangeRateSyncHistoryRepository historyRepository;

    @Autowired
    private ExchangeRateSyncSnapshotRepository snapshotRepository;

    @Autowired
    private ExchangeRateSyncFailureRepository failureRepository;

    @Test
    void savesCompletedHistory() {
        ExchangeRateSyncHistory history = historyRepository.save(new ExchangeRateSyncHistory(4));

        history.complete(4, true);
        historyRepository.flush();

        ExchangeRateSyncHistory saved = historyRepository.findById(history.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncStatus.COMPLETED);
        assertThat(saved.getRequestedBaseCurrencyCount()).isEqualTo(4);
        assertThat(saved.getSuccessCount()).isEqualTo(4);
        assertThat(saved.getFailureCount()).isZero();
        assertThat(saved.isActivated()).isTrue();
        assertThat(saved.getFinishedAt()).isNotNull();
    }

    @Test
    void savesReceivedSnapshot() {
        ExchangeRateSyncHistory history = historyRepository.save(new ExchangeRateSyncHistory(1));
        String payload = "{\"base_code\":\"USD\",\"conversion_rates\":{\"KRW\":1390.1}}";

        ExchangeRateSyncSnapshot snapshot = snapshotRepository.save(
                ExchangeRateSyncSnapshot.received(history, "USD", payload, "hash")
        );

        ExchangeRateSyncSnapshot saved = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(saved.getSyncHistory().getId()).isEqualTo(history.getId());
        assertThat(saved.getBaseCurrency()).isEqualTo("USD");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getPayloadHash()).isEqualTo("hash");
        assertThat(saved.getStatus()).isEqualTo(ExchangeRateSyncSnapshotStatus.RECEIVED);
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void savesFailureAndFindsUnresolvedFailures() {
        ExchangeRateSyncHistory history = historyRepository.save(new ExchangeRateSyncHistory(1));
        ExchangeRateSyncFailure failure = failureRepository.save(new ExchangeRateSyncFailure(
                history,
                "USD",
                ExchangeRateSyncFailureType.API_ERROR,
                null,
                "quota exceeded",
                true
        ));

        assertThat(failureRepository.findByResolvedFalse())
                .extracting(ExchangeRateSyncFailure::getId)
                .contains(failure.getId());
        assertThat(failureRepository.findById(failure.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getFailureType()).isEqualTo(ExchangeRateSyncFailureType.API_ERROR);
                    assertThat(saved.isRetryable()).isTrue();
                    assertThat(saved.isResolved()).isFalse();
                    assertThat(saved.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void findsSnapshotsAndFailuresByHistory() {
        ExchangeRateSyncHistory history = historyRepository.save(new ExchangeRateSyncHistory(2));
        snapshotRepository.save(ExchangeRateSyncSnapshot.received(history, "USD", "{}", "hash1"));
        snapshotRepository.save(ExchangeRateSyncSnapshot.failed(history, "JPY", "timeout"));
        failureRepository.save(new ExchangeRateSyncFailure(
                history,
                "JPY",
                ExchangeRateSyncFailureType.TIMEOUT,
                null,
                "timeout",
                true
        ));

        assertThat(snapshotRepository.findBySyncHistoryId(history.getId()))
                .extracting(ExchangeRateSyncSnapshot::getBaseCurrency)
                .containsExactlyInAnyOrder("USD", "JPY");
        assertThat(failureRepository.findBySyncHistoryId(history.getId()))
                .singleElement()
                .extracting(ExchangeRateSyncFailure::getBaseCurrency)
                .isEqualTo("JPY");
    }
}
