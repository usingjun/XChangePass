package bumblebee.xchangepass.domain.wallet.wallet.entity;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

import static bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferStatus.*;

@Getter
@Entity
@Table(name = "scheduled_transfer")
@NoArgsConstructor
public class ScheduledTransfer {
    @Id
    @GeneratedValue
    private Long scheduledTransferId;

    private Long senderId;

    private String receiverName;
    private String receiverPhoneNumber;

    private BigDecimal transferAmount;
    private Currency fromCurrency;
    private Currency toCurrency;
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    private WalletTransferStatus status = PENDING;

    public void markSuccess() {
        status = SUCCESS;
    }

    public void markFailed() {
        status = FAILED;
    }

    public ScheduledTransfer(Long senderId, WalletTransferRequest request) {
        this.senderId = senderId;
        this.receiverName = request.receiverName();
        this.receiverPhoneNumber = request.receiverPhoneNumber();
        this.transferAmount = request.transferAmount();
        this.fromCurrency = request.fromCurrency();
        this.toCurrency = request.toCurrency();
        this.scheduledAt = request.transferDatetime();
    }

}
