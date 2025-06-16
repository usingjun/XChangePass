package bumblebee.xchangepass.domain.wallet.transaction.entity;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.global.converter.CurrencyConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Entity
@Table(name = "wallet_transaction",
indexes = {
        @Index(name = "idx_wallet_user_time_id", columnList = "sender, updated_at, wallet_transaction_id DESC")
})
@Getter
@NoArgsConstructor()
@EntityListeners(AuditingEntityListener.class)
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_transaction_id", nullable = false)
    private Long walletTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver")
    private User receiver;

    @Column(nullable = false)
    private BigDecimal amount;

    @Convert(converter = CurrencyConverter.class)
    @Column(nullable = false)
    private Currency fromCurrency;

    @Convert(converter = CurrencyConverter.class)
    private Currency toCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionStatus status;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WalletTransaction(User sender, User receiver, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.transactionType = transactionType;
        this.status = WalletTransactionStatus.PENDING;
    }

    public WalletTransaction(User sender, User receiver, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType, WalletTransactionStatus status) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.transactionType = transactionType;
        this.status = status;
    }

    public void updateStatus(WalletTransactionStatus newStatus) {
        this.status = newStatus;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}