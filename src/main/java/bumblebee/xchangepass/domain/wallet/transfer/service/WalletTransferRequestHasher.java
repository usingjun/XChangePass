package bumblebee.xchangepass.domain.wallet.transfer.service;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class WalletTransferRequestHasher {

    public String hash(WalletTransferRequest request) {
        String canonicalRequest = String.join("|",
                request.receiverName().trim(),
                request.receiverPhoneNumber().replaceAll("\\D", ""),
                normalizeAmount(request.transferAmount()),
                request.fromCurrency().getCurrencyCode(),
                request.toCurrency().getCurrencyCode(),
                request.transferType().name()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
