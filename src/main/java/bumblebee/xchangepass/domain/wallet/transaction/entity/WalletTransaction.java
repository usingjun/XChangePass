package bumblebee.xchangepass.domain.wallet.transaction.entity;

import bumblebee.xchangepass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "wallet_transaction",
        indexes = {
                @Index(name = "idx_wallet_tx_user_time", columnList = "user_id,transaction_time DESC,transaction_id DESC"),
                @Index(name = "idx_wallet_tx_counterparty_time", columnList = "counterparty_user_id,transaction_time DESC,transaction_id DESC")
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

    @Column(precision = 19, scale = 4)
    private BigDecimal receivedAmount;

    @Column(name = "transfer_id", unique = true)
    private UUID transferId;

    @Column(length = 3)
    private String fromCurrency;

    @Column(length = 3)
    private String toCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionType transactionType;

    @Column(nullable = false)
    private LocalDateTime transactionTime;

    protected WalletTransaction() {
    }

    public WalletTransaction(User user, User counterpartyUser, BigDecimal amount, String fromCurrency,
                             String toCurrency, WalletTransactionType transactionType, LocalDateTime transactionTime) {
        this(user, counterpartyUser, amount, null, fromCurrency, toCurrency, transactionType, transactionTime);
    }

    public WalletTransaction(User user, User counterpartyUser, BigDecimal amount, BigDecimal receivedAmount,
                             String fromCurrency, String toCurrency, WalletTransactionType transactionType,
                             LocalDateTime transactionTime) {
        this(user, counterpartyUser, amount, receivedAmount, fromCurrency, toCurrency,
                transactionType, transactionTime, null);
    }

    public WalletTransaction(User user, User counterpartyUser, BigDecimal amount, BigDecimal receivedAmount,
                             String fromCurrency, String toCurrency, WalletTransactionType transactionType,
                             LocalDateTime transactionTime, UUID transferId) {
        this.user = user;
        this.counterpartyUser = counterpartyUser;
        this.amount = amount;
        this.receivedAmount = receivedAmount;
        this.transferId = transferId;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.transactionType = transactionType;
        this.transactionTime = transactionTime;
    }
}
