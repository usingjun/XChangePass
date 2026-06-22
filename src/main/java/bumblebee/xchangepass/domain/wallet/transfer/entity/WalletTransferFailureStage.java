package bumblebee.xchangepass.domain.wallet.transfer.entity;

public enum WalletTransferFailureStage {
    PARTICIPANT_VALIDATION,
    FRAUD_VALIDATION,
    LOCK_ACQUISITION,
    BALANCE_VALIDATION,
    FUNDS_AND_LEDGER_TRANSACTION,
    UNKNOWN
}
