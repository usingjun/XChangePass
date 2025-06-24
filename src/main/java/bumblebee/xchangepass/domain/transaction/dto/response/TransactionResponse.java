package bumblebee.xchangepass.domain.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Currency;

@Getter
public class TransactionResponse{
        private Long userId;
        private Currency beforeCurrency;
        private Currency afterCurrency;
        private LocalDateTime transactionTime;

        @JsonTypeInfo(
                use = JsonTypeInfo.Id.CLASS,
                include = JsonTypeInfo.As.PROPERTY,
                property = "@class"
        )
        private TransactionDataDto data;

        // 필수: 기본 생성자
        public TransactionResponse() {}

        // 생성자
        public TransactionResponse(Long userId, Currency beforeCurrency, Currency afterCurrency, LocalDateTime transactionTime, TransactionDataDto data) {
                this.userId = userId;
                this.beforeCurrency = beforeCurrency;
                this.afterCurrency = afterCurrency;
                this.transactionTime = transactionTime;
                this.data = data;
        }
}
