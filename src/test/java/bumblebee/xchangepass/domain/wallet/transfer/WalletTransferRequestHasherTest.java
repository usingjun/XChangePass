package bumblebee.xchangepass.domain.wallet.transfer;

import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferRequestHasher;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class WalletTransferRequestHasherTest {

    private final WalletTransferRequestHasher hasher = new WalletTransferRequestHasher();

    @Test
    void normalizesPhoneNumberWhitespaceAndAmountScale() {
        WalletTransferRequest first = request(" receiver ", "010-1234-5678", new BigDecimal("1000.00"));
        WalletTransferRequest second = request("receiver", "01012345678", new BigDecimal("1000"));

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second));
    }

    @Test
    void differentAmountProducesDifferentHash() {
        assertThat(hasher.hash(request("receiver", "01012345678", new BigDecimal("1000"))))
                .isNotEqualTo(hasher.hash(request("receiver", "01012345678", new BigDecimal("1001"))));
    }

    private WalletTransferRequest request(String name, String phone, BigDecimal amount) {
        return new WalletTransferRequest(
                name,
                phone,
                amount,
                Currency.getInstance("KRW"),
                Currency.getInstance("KRW"),
                null,
                WalletTransferType.GENERAL
        );
    }
}
