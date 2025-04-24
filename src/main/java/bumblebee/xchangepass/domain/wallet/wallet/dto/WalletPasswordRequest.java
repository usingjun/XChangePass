package bumblebee.xchangepass.domain.wallet.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public record WalletPasswordRequest(
        @NotBlank String password
) {}

