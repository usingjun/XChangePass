package bumblebee.xchangepass.domain.wallet.reconciliation.repository;

import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletLedgerAggregationResult;
import bumblebee.xchangepass.domain.wallet.reconciliation.dto.WalletLedgerBalanceAggregate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class WalletLedgerAggregationRepositoryImpl implements WalletLedgerAggregationRepository {

    private static final String LEDGER_BALANCE_AGGREGATION_SQL = """
            select ledger_user_id, currency, sum(ledger_amount) as ledger_calculated_amount
            from (
                select user_id as ledger_user_id, to_currency as currency, amount as ledger_amount
                from wallet_transaction
                where transaction_type = 'DEPOSIT'
                union all
                select user_id as ledger_user_id, to_currency as currency, -amount as ledger_amount
                from wallet_transaction
                where transaction_type = 'WITHDRAWAL'
                union all
                select user_id as ledger_user_id, from_currency as currency, -amount as ledger_amount
                from wallet_transaction
                where transaction_type = 'TRANSFER'
                union all
                select counterparty_user_id as ledger_user_id,
                       to_currency as currency,
                       case
                           when received_amount is not null then received_amount
                           when from_currency = to_currency then amount
                           else null
                       end as ledger_amount
                from wallet_transaction
                where transaction_type = 'TRANSFER'
            ) ledger_entries
            where ledger_user_id is not null
              and currency is not null
              and ledger_amount is not null
            group by ledger_user_id, currency
            """;

    private static final String UNCALCULABLE_LEDGER_COUNT_SQL = """
            select count(*)
            from wallet_transaction
            where transaction_type = 'TRANSFER'
              and received_amount is null
              and (from_currency is null or to_currency is null or from_currency <> to_currency)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public WalletLedgerAggregationResult aggregate() {
        List<WalletLedgerBalanceAggregate> balances = findLedgerBalances();
        int uncalculableLedgerCount = countUncalculableLedgers();
        return new WalletLedgerAggregationResult(balances, uncalculableLedgerCount);
    }

    private List<WalletLedgerBalanceAggregate> findLedgerBalances() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(LEDGER_BALANCE_AGGREGATION_SQL)
                .getResultList();
        return rows.stream()
                .map(row -> new WalletLedgerBalanceAggregate(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        toBigDecimal(row[2])
                ))
                .toList();
    }

    private int countUncalculableLedgers() {
        Number count = (Number) entityManager.createNativeQuery(UNCALCULABLE_LEDGER_COUNT_SQL)
                .getSingleResult();
        return Math.toIntExact(count.longValue());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }
}
