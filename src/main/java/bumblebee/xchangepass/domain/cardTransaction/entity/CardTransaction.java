package bumblebee.xchangepass.domain.cardTransaction.entity;

import bumblebee.xchangepass.domain.transaction.entity.ProjectionState;
import bumblebee.xchangepass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "card_transaction",
        indexes = {
                @Index(name = "idx_card_tx_projection", columnList = "projection_status,next_projection_at"),
                @Index(name = "idx_card_tx_user_time", columnList = "user_id,transaction_time")
        }
)
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String merchantName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(nullable = false, length = 3)
    private String approvedCurrency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal krwAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(nullable = false, unique = true, length = 30)
    private String approvalNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardTransactionType transactionType;

    @Column(nullable = false)
    private LocalDateTime transactionTime;

    @Embedded
    private ProjectionState projection;

    protected CardTransaction() {
    }

    public CardTransaction(User user, String merchantName, BigDecimal approvedAmount, String approvedCurrency,
                           BigDecimal krwAmount, BigDecimal balanceAfter, String approvalNumber,
                           CardTransactionType transactionType, LocalDateTime transactionTime) {
        this.user = user;
        this.merchantName = merchantName;
        this.approvedAmount = approvedAmount;
        this.approvedCurrency = approvedCurrency;
        this.krwAmount = krwAmount;
        this.balanceAfter = balanceAfter;
        this.approvalNumber = approvalNumber;
        this.transactionType = transactionType;
        this.transactionTime = transactionTime;
        this.projection = ProjectionState.pending(transactionTime);
    }
}
