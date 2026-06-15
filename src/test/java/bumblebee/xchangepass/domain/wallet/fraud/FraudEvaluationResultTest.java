package bumblebee.xchangepass.domain.wallet.fraud;

import bumblebee.xchangepass.domain.wallet.fraud.service.FraudEvaluationResult;
import bumblebee.xchangepass.domain.wallet.fraud.service.FraudReason;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudEvaluationResultTest {

    @Test
    void resultRejectsInconsistentReasonAndScore() {
        assertThatThrownBy(() -> new FraudEvaluationResult(
                EnumSet.of(FraudReason.FREQUENCY_EXCEEDED), 0
        ))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new FraudEvaluationResult(
                EnumSet.of(FraudReason.CLEAR, FraudReason.FREQUENCY_EXCEEDED), 40
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultCanContainMultipleReasonsAndRiskScore() {
        FraudEvaluationResult result = FraudEvaluationResult.suspicious(
                EnumSet.of(FraudReason.FREQUENCY_EXCEEDED, FraudReason.REPEATED_AMOUNT),
                70
        );

        assertThat(result.suspicious()).isTrue();
        assertThat(result.reasons()).containsExactlyInAnyOrder(
                FraudReason.FREQUENCY_EXCEEDED, FraudReason.REPEATED_AMOUNT
        );
        assertThat(result.riskScore()).isEqualTo(70);
    }
}
