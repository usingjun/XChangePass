package bumblebee.xchangepass.domain.cardTransaction.entity;

import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.global.converter.CurrencyConverter;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "card_transaction",
indexes = {
        @Index(name = "idx_card_user_time_id", columnList = "user_id, transaction_time, transaction_id DESC")
})
@EntityListeners(AuditingEntityListener.class)
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "approved_amount", nullable = false)
    private BigDecimal approvedAmount;

    @Convert(converter = CurrencyConverter.class)
    @Column(name = "approved_currency", nullable = false, length = 3)
    private Currency approvedCurrency;

    @Column(name = "krw_amount", nullable = false)
    private BigDecimal krwAmount;

    @Column(name = "transaction_time", nullable = false)
    private LocalDateTime transactionTime;

    @Column(name = "approval_number", nullable = false, length = 20)
    private String approvalNumber;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CardTransaction(User user,
                           String merchantName,
                           BigDecimal approvedAmount,
                           Currency approvedCurrency,
                           BigDecimal krwAmount,
                           LocalDateTime transactionTime,
                           String approvalNumber,
                           BigDecimal balanceAfter,
                           TransactionType transactionType) {
        this.user = user;
        this.merchantName = merchantName;
        this.approvedAmount = approvedAmount;
        this.approvedCurrency = approvedCurrency;
        this.krwAmount = krwAmount;
        this.transactionTime = transactionTime;
        this.approvalNumber = approvalNumber;
        this.balanceAfter = balanceAfter;
        this.transactionType = transactionType;
    }
}
