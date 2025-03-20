package bumblebee.xchangepass.domain.wallet.balance.entity;

import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.converter.CurrencyConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Entity
@Table(name = "balance")
@Getter
@NoArgsConstructor()
@EntityListeners(AuditingEntityListener.class)
public class WalletBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Convert(converter = CurrencyConverter.class)
    private Currency currency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @CreatedDate
    private LocalDateTime currencyCreatedAt;

    @LastModifiedDate
    private LocalDateTime currencyModifiedAt;

    public WalletBalance(Wallet wallet, Currency currency) {
        this.wallet = wallet;
        this.currency = currency;
    }

    public void addBalance(BigDecimal balance) {
        this.balance = this.balance.add(balance);
    }

    public void subtractBalance(BigDecimal balance) {
        this.balance = this.balance.subtract(balance);
    }
}