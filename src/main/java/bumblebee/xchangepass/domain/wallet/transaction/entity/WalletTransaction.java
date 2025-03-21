package bumblebee.xchangepass.domain.wallet.transaction.entity;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Entity
@Table(name = "wallet_transaction")
@Getter
@NoArgsConstructor()
@EntityListeners(AuditingEntityListener.class)
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_transaction_id", nullable = false)
    private Long walletTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet myWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet counterWallet;

    @Column(nullable = false)
    private BigDecimal amount;

    private Currency fromCurrency;

    @Column(nullable = false)
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

    public WalletTransaction(Wallet myWallet, Wallet counterWallet, BigDecimal amount, Currency fromCurrency, Currency toCurrency, WalletTransactionType transactionType) {
        this.myWallet = myWallet;
        this.counterWallet = counterWallet;
        this.amount = amount;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.transactionType = transactionType;
        this.status = WalletTransactionStatus.PENDING;
    }

    public void updateStatus(WalletTransactionStatus newStatus) {
        this.status = newStatus;
    }
}