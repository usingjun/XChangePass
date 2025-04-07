package bumblebee.xchangepass.domain.wallet.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FraudRuleEvaluator {

    private final RedisFraudStorageService redisFraudStorage;

    public boolean isSuspicious(Long userId, BigDecimal latestAmount) {
        List<FraudRecord> history = redisFraudStorage.getRecentTransactions(userId);

        return isOverTotalLimit(history, latestAmount)
               || isTooFrequent(history)
               || isRepeatedAmount(history, latestAmount)
               || isNightTransaction();
    }

    // 🔸 룰 1: 10분 내 누적 금액
    private boolean isOverTotalLimit(List<FraudRecord> history, BigDecimal latestAmount) {
        BigDecimal sum = history.stream()
                .map(FraudRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.add(latestAmount).compareTo(new BigDecimal("500000")) > 0;
    }

    // 🔸 룰 2: 최근 5분간 5건 이상
    private boolean isTooFrequent(List<FraudRecord> history) {
        LocalDateTime fiveMinAgo = LocalDateTime.now().minusMinutes(5);
        long recentCount = history.stream()
                .filter(r -> r.timestamp().isAfter(fiveMinAgo))
                .count();
        return recentCount >= 5;
    }

    // 🔸 룰 3: 최근 거래 3건이 동일 금액
    private boolean isRepeatedAmount(List<FraudRecord> history, BigDecimal latestAmount) {
        List<BigDecimal> recentAmounts = history.stream()
                .map(FraudRecord::amount)
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .toList();

        return recentAmounts.size() == 3 && recentAmounts.stream().allMatch(latestAmount::equals);
    }

    // 🔸 룰 4: 심야 시간대 거래 (02:30~03:30)
    private boolean isNightTransaction() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(2, 30)) && now.isBefore(LocalTime.of(3, 30));
    }
}
