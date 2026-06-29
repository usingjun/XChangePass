package bumblebee.xchangepass.domain.exchangeRate.entity;

public enum ExchangeRateSyncFailureType {
    API_ERROR,
    TIMEOUT,
    NULL_RESPONSE,
    BASE_CURRENCY_MISMATCH,
    MISSING_CONVERSION_RATES,
    MISSING_REQUIRED_CURRENCY,
    INVALID_RATE_VALUE,
    SAVE_FAILED,
    SWAP_FAILED
}
