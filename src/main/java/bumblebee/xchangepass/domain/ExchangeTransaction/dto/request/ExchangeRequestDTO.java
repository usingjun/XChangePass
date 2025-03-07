package bumblebee.xchangepass.domain.ExchangeTransaction.dto.request;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Builder
public record ExchangeRequestDTO (
        Long userId,
     String fromCurrency,
     String toCurrency,
     BigDecimal amount
){}
