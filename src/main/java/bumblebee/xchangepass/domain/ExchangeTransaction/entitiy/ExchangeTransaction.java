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
    private Long exchangeTransactionId;  // 거래 ID

    @Column(nullable = false)
    private Long userId;  // 사용자 ID

    @Column(nullable = false, length = 10)
    private String fromCurrency;  // 환전 전 통화

    @Column(nullable = false, length = 10)
    private String toCurrency;  // 환전 후 통화

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal exchangeRate;  // 환율

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;  // 환전할 금액

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedAmount;  // 환전 후 받는 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;  // 거래 상태
}
