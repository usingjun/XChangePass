package bumblebee.xchangepass.domain.wallet.transaction.entity;

import bumblebee.xchangepass.domain.transaction.entity.ProjectionState;
import bumblebee.xchangepass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "wallet_transaction",
        indexes = {
                @Index(name = "idx_wallet_tx_projection", columnList = "projection_status,next_projection_at"),
                @Index(name = "idx_wallet_tx_user_time", columnList = "user_id,transaction_time")
        }
)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_user_id")
    private User counterpartyUser;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String fromCurrency;

    @Column(length = 3)
    private String toCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionType transactionType;

    @Column(nullable = false)
    private LocalDateTime transactionTime;

    @Embedded
    private ProjectionState projection;

    protected WalletTransaction() {
    }

    public WalletTransaction(User user, User counterpartyUser, BigDecimal amount, String fromCurrency,
                             String toCurrency, WalletTransactionType transactionType, LocalDateTime transactionTime) {
        this.user = user;
        this.counterpartyUser = counterpartyUser;
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.transactionType = transactionType;
        this.transactionTime = transactionTime;
        this.projection = ProjectionState.pending(transactionTime);
    }
}
