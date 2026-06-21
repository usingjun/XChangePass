# Fraud Redis Resilience Operations

## Responsibility

Resilience4j limits calls to an unavailable Redis instance and controls recovery probes. It does not guarantee financial consistency. PostgreSQL transactions, wallet-scoped Advisory Locks, balance and ledger atomicity, and Fail-Closed ordering provide the financial safety boundary.

## Runtime flow

```text
CircuitBreaker(Retry(Redis Lua))
```

- A transaction may call Redis at most twice.
- CircuitBreaker records the final Retry outcome once.
- OPEN requests do not invoke Retry or Redis.
- HALF_OPEN permits two transaction probes, each with at most two Redis attempts.

## States

- `CLOSED`: normal Redis fraud evaluation.
- `OPEN`: fraud evaluation is unavailable and the transaction is blocked before balance mutation.
- `HALF_OPEN`: two limited transaction probes verify Redis recovery.

Circuit state is local to each application instance. Multiple application instances do not share Sliding Window samples or state transitions.

## Metrics

- `resilience4j_circuitbreaker_state`
- `resilience4j_circuitbreaker_calls_seconds`
- `resilience4j_circuitbreaker_slow_calls`
- `resilience4j_circuitbreaker_not_permitted_calls_total`
- `fraud_circuitbreaker_transitions_total{from,to}`

Actuator endpoints:

- `/actuator/metrics`
- `/actuator/prometheus`
- `/actuator/circuitbreakers`
- `/actuator/circuitbreakerevents`

## Health probe caution

Do not use the aggregate CircuitBreaker health result directly as a liveness restart condition. A shared Redis outage can open every instance and cause simultaneous restarts without repairing Redis. Keep process liveness separate from dependency readiness and alert on the CircuitBreaker metrics and transitions.
