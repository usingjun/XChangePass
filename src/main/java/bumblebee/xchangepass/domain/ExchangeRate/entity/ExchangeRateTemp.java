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
@Table(name = "exchange_rate_temp")
@Getter
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRateTemp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String baseCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Double> exchangeRates;


    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ExchangeRateTemp(String baseCurrency, Map<String, Double> rate) {
        this.baseCurrency = baseCurrency;
        this.exchangeRates = rate;
        this.updatedAt = LocalDateTime.now();
    }
}
