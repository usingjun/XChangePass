package bumblebee.xchangepass.domain.wallet.fraud.service;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public record FraudEvaluationResult(
        Set<FraudReason> reasons,
        int riskScore
) {
    public FraudEvaluationResult {
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("Fraud reasons must not be empty");
        }
        reasons = Set.copyOf(reasons);
        if (reasons.contains(FraudReason.CLEAR) && reasons.size() > 1) {
            throw new IllegalArgumentException("CLEAR cannot be combined with suspicious reasons");
        }
        if ((reasons.contains(FraudReason.CLEAR) && riskScore != 0)
                || (!reasons.contains(FraudReason.CLEAR) && riskScore <= 0)) {
            throw new IllegalArgumentException("Fraud reasons and risk score must be consistent");
        }
    }

    public boolean suspicious() {
        return !reasons.contains(FraudReason.CLEAR);
    }

    public String description() {
        return reasons.stream()
                .filter(reason -> reason != FraudReason.CLEAR)
                .sorted()
                .map(FraudReason::description)
                .collect(Collectors.joining(", ")) + " (위험 점수: " + riskScore + ")";
    }

    public static FraudEvaluationResult clear() {
        return new FraudEvaluationResult(EnumSet.of(FraudReason.CLEAR), 0);
    }

    public static FraudEvaluationResult suspicious(FraudReason reason, int riskScore) {
        return new FraudEvaluationResult(EnumSet.of(reason), riskScore);
    }

    public static FraudEvaluationResult suspicious(Set<FraudReason> reasons, int riskScore) {
        return new FraudEvaluationResult(reasons, riskScore);
    }
}
