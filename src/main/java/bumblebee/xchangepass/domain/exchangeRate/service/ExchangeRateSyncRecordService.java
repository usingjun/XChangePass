package bumblebee.xchangepass.domain.exchangeRate.service;

import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailure;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncFailureType;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncHistory;
import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateSyncSnapshot;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncFailureRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncHistoryRepository;
import bumblebee.xchangepass.domain.exchangeRate.repository.ExchangeRateSyncSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ExchangeRateSyncRecordService {

    private final ExchangeRateSyncHistoryRepository historyRepository;
    private final ExchangeRateSyncSnapshotRepository snapshotRepository;
    private final ExchangeRateSyncFailureRepository failureRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExchangeRateSyncHistory start(int requestedBaseCurrencyCount) {
        if (requestedBaseCurrencyCount < 1) {
            throw new IllegalArgumentException("requestedBaseCurrencyCount must be greater than 0");
        }
        return historyRepository.save(new ExchangeRateSyncHistory(requestedBaseCurrencyCount));
    }

    @Transactional
    public ExchangeRateSyncSnapshot recordReceivedSnapshot(ExchangeRateSyncHistory history,
                                                           String baseCurrency,
                                                           ExchangeRateResponse response) {
        String payload = serialize(response);
        return snapshotRepository.save(
                ExchangeRateSyncSnapshot.received(history, baseCurrency, payload, sha256(payload))
        );
    }

    @Transactional
    public ExchangeRateSyncSnapshot recordFailedSnapshot(ExchangeRateSyncHistory history,
                                                         String baseCurrency,
                                                         String errorMessage) {
        return snapshotRepository.save(
                ExchangeRateSyncSnapshot.failed(history, baseCurrency, errorMessage)
        );
    }

    @Transactional
    public ExchangeRateSyncFailure recordFailure(ExchangeRateSyncHistory history,
                                                 String baseCurrency,
                                                 ExchangeRateSyncFailureType failureType,
                                                 String rawPayload,
                                                 String errorMessage,
                                                 boolean retryable) {
        return failureRepository.save(new ExchangeRateSyncFailure(
                history,
                baseCurrency,
                failureType,
                rawPayload,
                errorMessage,
                retryable
        ));
    }

    @Transactional
    public ExchangeRateSyncFailure recordValidationFailure(ExchangeRateSyncHistory history,
                                                           String baseCurrency,
                                                           String rawPayload,
                                                           ExchangeRateValidationResult validationResult) {
        if (validationResult == null || validationResult.valid()) {
            throw new IllegalArgumentException("invalid validationResult is required");
        }
        return recordFailure(
                history,
                baseCurrency,
                validationResult.failureType(),
                rawPayload,
                validationResult.errorMessage(),
                true
        );
    }

    @Transactional
    public void complete(ExchangeRateSyncHistory history, int successCount, boolean activated) {
        history.complete(successCount, activated);
    }

    @Transactional
    public void partialFail(ExchangeRateSyncHistory history,
                            int successCount,
                            int failureCount,
                            String errorMessage) {
        history.partialFail(successCount, failureCount, errorMessage);
    }

    @Transactional
    public void fail(ExchangeRateSyncHistory history, String errorMessage) {
        history.fail(errorMessage);
    }

    private String serialize(ExchangeRateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize exchange rate response", exception);
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
