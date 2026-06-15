package bumblebee.xchangepass.domain.transaction.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionStateTest {

    @Test
    void failedProjectionSchedulesRetryWithoutLosingRdbTransaction() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        ProjectionState projection = ProjectionState.pending(transactionTime);

        projection.failed("MongoDB unavailable", transactionTime);

        assertThat(projection.getStatus()).isEqualTo(ProjectionStatus.RETRY);
        assertThat(projection.getAttempts()).isEqualTo(1);
        assertThat(projection.getNextAttemptAt()).isAfter(transactionTime);
    }

    @Test
    void waitingProjectionBecomesPendingAfterTransactionCompletion() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 1, 1, 1, 0);
        ProjectionState projection = ProjectionState.waiting();

        projection.ready(completedAt);

        assertThat(projection.getStatus()).isEqualTo(ProjectionStatus.PENDING);
        assertThat(projection.getNextAttemptAt()).isEqualTo(completedAt);
    }
}
