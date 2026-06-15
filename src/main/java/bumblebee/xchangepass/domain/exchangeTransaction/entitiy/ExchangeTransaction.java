package bumblebee.xchangepass.domain.exchangeTransaction.entitiy;

import bumblebee.xchangepass.domain.transaction.entity.ProjectionState;
import bumblebee.xchangepass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "exchange_transaction",
        indexes = {
                @Index(name = "idx_exchange_tx_projection", columnList = "projection_status,next_projection_at"),
                @Index(name = "idx_exchange_tx_user_time", columnList = "user_id,created_at")
        }
)
public class ExchangeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 3)
    private String fromCurrency;

    @Column(nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedAmount;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeTransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Embedded
    private ProjectionState projection;

    protected ExchangeTransaction() {
    }

    public ExchangeTransaction(User user, String fromCurrency, String toCurrency, BigDecimal amount,
                               BigDecimal receivedAmount, BigDecimal exchangeRate, LocalDateTime createdAt) {
        this.user = user;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.amount = amount;
        this.receivedAmount = receivedAmount;
        this.exchangeRate = exchangeRate;
        this.status = ExchangeTransactionStatus.PENDING;
        this.createdAt = createdAt;
        this.projection = ProjectionState.waiting();
    }

    public void complete(LocalDateTime completedAt) {
        if (status == ExchangeTransactionStatus.COMPLETED) {
            throw new IllegalStateException("Exchange transaction is already completed: " + transactionId);
        }
        this.status = ExchangeTransactionStatus.COMPLETED;
        this.completedAt = completedAt;
        this.projection.ready(completedAt);
    }
}
