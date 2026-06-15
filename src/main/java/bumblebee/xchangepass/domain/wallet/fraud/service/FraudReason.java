package bumblebee.xchangepass.domain.wallet.fraud.service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum FraudReason {
    CLEAR("0", ""),
    TOTAL_AMOUNT_EXCEEDED("1", "누적 금액 초과"),
    FREQUENCY_EXCEEDED("2", "5분 내 거래 횟수 초과"),
    REPEATED_AMOUNT("3", "동일 금액 반복"),
    NIGHT_TIME_TRANSACTION("4", "심야 시간대 거래");

    private final String code;
    private final String description;

    FraudReason(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String description() {
        return description;
    }

    public static FraudReason fromCode(String code) {
        return Arrays.stream(values())
                .filter(reason -> reason.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unexpected fraud evaluation result: " + code));
    }

    public static Set<FraudReason> fromCodes(String codes) {
        if (codes == null || codes.isBlank() || CLEAR.code.equals(codes)) {
            return EnumSet.of(CLEAR);
        }

        EnumSet<FraudReason> reasons = EnumSet.noneOf(FraudReason.class);
        Arrays.stream(codes.split(","))
                .map(FraudReason::fromCode)
                .filter(reason -> reason != CLEAR)
                .forEach(reasons::add);
        return reasons.isEmpty() ? EnumSet.of(CLEAR) : reasons;
    }
}
