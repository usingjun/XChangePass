package bumblebee.xchangepass.domain.ExchangeRate.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(name = "exchange_rate")
@Getter
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 기본 키

    @Column(nullable = false)
    private String baseCurrency; // 기준 통화 (예: USD, EUR)


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")// Ensure that PostgreSQL uses the jsonb type
    private Map<String, Double> exchangeRates;

    @Setter
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 갱신 시간

    @Builder
    public ExchangeRate(String baseCurrency, Map<String, Double> rate) {
        this.baseCurrency = baseCurrency;
        this.exchangeRates = rate;
        this.updatedAt = LocalDateTime.now();
    }

}
