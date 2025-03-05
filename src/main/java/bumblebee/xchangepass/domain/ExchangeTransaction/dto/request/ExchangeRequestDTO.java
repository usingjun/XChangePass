package bumblebee.xchangepass.domain.ExchangeTransaction.dto.request;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Builder
public record ExchangeRequestDTO (
        Long userId,
     String fromCurrency, // 환전 전 통화
     String toCurrency,  // 환전 후 통화
     BigDecimal amount
){}
