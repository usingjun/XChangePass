package bumblebee.xchangepass.domain.wallet.reconciliation.repository;

import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletLedgerAggregationResult;

public interface WalletLedgerAggregationRepository {

    WalletLedgerAggregationResult aggregate();
}
