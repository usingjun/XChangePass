package bumblebee.xchangepass.domain.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Embeddable
public class ProjectionState {

    @Enumerated(EnumType.STRING)
    @Column(name = "projection_status", nullable = false, length = 20)
    private ProjectionStatus status;

    @Column(name = "projection_attempts", nullable = false)
    private int attempts;

    @Column(name = "next_projection_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_projection_error", length = 1000)
    private String lastError;

    protected ProjectionState() {
    }

    private ProjectionState(ProjectionStatus status, LocalDateTime nextAttemptAt) {
        this.status = status;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static ProjectionState pending(LocalDateTime transactionTime) {
        return new ProjectionState(ProjectionStatus.PENDING, transactionTime);
    }

    public static ProjectionState waiting() {
        return new ProjectionState(ProjectionStatus.WAITING, null);
    }

    public void ready(LocalDateTime completedAt) {
        this.status = ProjectionStatus.PENDING;
        this.nextAttemptAt = completedAt;
    }

    public void projected() {
        this.status = ProjectionStatus.PROJECTED;
        this.lastError = null;
    }

    public void failed(String errorMessage, LocalDateTime failedAt) {
        this.status = ProjectionStatus.RETRY;
        this.attempts++;
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        this.nextAttemptAt = failedAt.plusSeconds(delaySeconds);
        this.lastError = truncate(errorMessage);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown projection error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
