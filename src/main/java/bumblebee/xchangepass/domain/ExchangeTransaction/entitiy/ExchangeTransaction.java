package bumblebee.xchangepass.domain.ExchangeTransaction.entitiy;

import bumblebee.xchangepass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
public class ExchangeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long exchangeTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 10)
    private String fromCurrency;

    @Column(nullable = false, length = 10)
    private String toCurrency;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Builder
    public ExchangeTransaction(User user, String fromCurrency, String toCurrency,
                               BigDecimal exchangeRate, BigDecimal amount, BigDecimal receivedAmount, TransactionStatus status) {
        this.user = user;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.exchangeRate = exchangeRate;
        this.amount = amount;
        this.receivedAmount = receivedAmount;
        this.status = status;
    }
}
