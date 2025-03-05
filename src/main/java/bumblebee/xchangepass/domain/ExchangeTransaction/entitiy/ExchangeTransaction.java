package bumblebee.xchangepass.domain.ExchangeTransaction.entitiy;

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
@Builder
@AllArgsConstructor
public class ExchangeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long exchangeTransactionId;

    @Column(nullable = false)
    private Long userId;

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
}
