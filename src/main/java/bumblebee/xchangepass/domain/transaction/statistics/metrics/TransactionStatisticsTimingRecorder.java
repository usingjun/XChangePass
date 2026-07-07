package bumblebee.xchangepass.domain.transaction.statistics.metrics;

import bumblebee.xchangepass.domain.transaction.statistics.dto.TransactionStatisticsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public class TransactionStatisticsTimingRecorder {

    private static final Logger log = LoggerFactory.getLogger(TransactionStatisticsTimingRecorder.class);
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final String UNKNOWN = "unknown";
    private static final TransactionStatisticsTimingRecorder CONFIGURED = new TransactionStatisticsTimingRecorder(
            resolveSystemEnabled(),
            resolveSystemOutputPath()
    );

    private final boolean enabled;
    private final Path outputPath;

    private TransactionStatisticsTimingRecorder(boolean enabled, String configuredPath) {
        this.enabled = enabled;
        this.outputPath = StringUtils.hasText(configuredPath) ? Path.of(configuredPath) : null;
        if (enabled) {
            log.info("Transaction statistics timing recorder configured: outputPath={}", outputPath);
        }
        initialize();
    }

    public static TransactionStatisticsTimingRecorder configured() {
        return CONFIGURED;
    }

    private static boolean resolveSystemEnabled() {
        return Boolean.parseBoolean(System.getenv("TRANSACTION_STATISTICS_TIMING_ENABLED"))
                || Boolean.getBoolean("transaction.statistics.timing.enabled");
    }

    private static String resolveSystemOutputPath() {
        String configuredPath = System.getenv("TRANSACTION_STATISTICS_TIMING_OUTPUT");
        if (!StringUtils.hasText(configuredPath)) {
            configuredPath = System.getProperty("transaction.statistics.timing.output");
        }
        return configuredPath;
    }

    public <T> T recordRequest(TransactionStatisticsMode mode, Supplier<T> supplier) {
        if (!enabled) {
            return supplier.get();
        }

        REQUEST_ID.set(UUID.randomUUID().toString());
        try {
            return record("statistics.api.total", mode, UNKNOWN, UNKNOWN, -1, supplier);
        } finally {
            REQUEST_ID.remove();
        }
    }

    public <T> T recordService(TransactionStatisticsMode mode, String metric, Supplier<T> supplier) {
        return record(metric, mode, UNKNOWN, UNKNOWN, -1, supplier);
    }

    public <T> T recordRepository(
            TransactionStatisticsMode mode, String metric, String datasourceTarget, Supplier<T> supplier
    ) {
        return record(metric, mode, datasourceTarget, UNKNOWN, -1, supplier);
    }

    public void recordCompleted(
            String metric, TransactionStatisticsMode mode, String datasourceTarget, long durationNanos, int rowCount
    ) {
        if (!enabled) {
            return;
        }
        write(metric, mode, datasourceTarget, UNKNOWN, durationNanos, rowCount);
    }

    private <T> T record(
            String metric,
            TransactionStatisticsMode mode,
            String datasourceTarget,
            String detail,
            int rowCount,
            Supplier<T> supplier
    ) {
        if (!enabled) {
            return supplier.get();
        }

        long startedAt = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            write(metric, mode, datasourceTarget, detail, System.nanoTime() - startedAt, rowCount);
        }
    }

    private synchronized void write(
            String metric,
            TransactionStatisticsMode mode,
            String datasourceTarget,
            String detail,
            long durationNanos,
            int rowCount
    ) {
        if (outputPath == null) {
            return;
        }

        String row = String.join(",",
                Instant.now().toString(),
                value(REQUEST_ID.get()),
                value(metric),
                value(mode == null ? UNKNOWN : mode.name()),
                value(datasourceTarget),
                value(detail),
                Long.toString(durationNanos),
                toMillis(durationNanos),
                Integer.toString(rowCount)
        ) + System.lineSeparator();

        try {
            Files.writeString(outputPath, row, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Local benchmark timing must never affect API behavior.
        }
    }

    private void initialize() {
        if (!enabled || outputPath == null) {
            return;
        }

        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(outputPath)) {
                Files.writeString(
                        outputPath,
                        "recorded_at,request_id,metric,mode,datasource_target,detail,duration_nanos,duration_ms,row_count"
                                + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE
                );
            }
        } catch (IOException exception) {
            // Local benchmark timing must never affect application startup.
            log.warn("Failed to initialize transaction statistics timing output: {}", outputPath, exception);
        }
    }

    private String toMillis(long nanos) {
        return BigDecimal.valueOf(nanos)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String value(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "_");
    }
}
