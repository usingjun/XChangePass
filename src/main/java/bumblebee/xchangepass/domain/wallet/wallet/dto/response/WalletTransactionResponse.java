package bumblebee.xchangepass.domain.wallet.wallet.dto.response;

import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionStatus;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Schema(description = "송금 응답 객체")
public record WalletTransactionResponse(
        @Schema(description = "거래내역 ID", example = "1")
        Long transactionId,

        @Schema(description = "송금 금액", example = "10000.00")
        BigDecimal amount,

        @Schema(description = "가지고 계신 화폐 종류", example = "KRW, USD")
        Currency fromCurrency,

        @Schema(description = "보내고자 하는 화폐 종류", example = "KRW, USD")
        Currency toCurrency,

        @Schema(description = "거래 종류", example = "DEPOSIT, WITHDRAWAL, TRANSFER")
        WalletTransactionType transactionType,

        @Schema(description = "거래 진행 상황", example = "PENDING, SUCCESS, FAILED")
        WalletTransactionStatus status,

        @Schema(description = "거래 완료된 시간", example = "2024-02-20T12:34:56")
        LocalDateTime updatedAt,

        @Schema(description = "받으시는 분 walletId", example = "1")
        Long counterWalletId,

        @Schema(description = "받으시는 분 이름", example = "홍길동")
        String counterpartyName
) {
    public static WalletTransactionResponse fromEntity(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getWalletTransactionId(),
                transaction.getAmount(),
                transaction.getFromCurrency(),
                transaction.getToCurrency(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getUpdatedAt(),
                transaction.getCounterWallet().getWalletId(),
                transaction.getCounterWallet().getUser().getUserName().getValue()
        );
    }
}
