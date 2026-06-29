package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;

public record ExchangeRateValidationResult(
        boolean validResult,
        ExchangeRateSyncFailureType failureType,
        String errorMessage
) {

    public boolean valid() {
        return validResult;
    }

    public static ExchangeRateValidationResult success() {
        return new ExchangeRateValidationResult(true, null, null);
    }

    public static ExchangeRateValidationResult invalid(ExchangeRateSyncFailureType failureType,
                                                       String errorMessage) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType is required for invalid result");
        }
        return new ExchangeRateValidationResult(false, failureType, errorMessage);
    }
}
