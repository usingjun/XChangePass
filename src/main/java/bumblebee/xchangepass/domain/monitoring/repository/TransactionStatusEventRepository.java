package bumblebee.xchangepass.domain.monitoring.repository;

import bumblebee.xchangepass.domain.monitoring.entity.TransactionStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionStatusEventRepository extends JpaRepository<TransactionStatusEvent, Long> {

    List<TransactionStatusEvent> findByTransactionIdOrderByOccurredAtAscIdAsc(UUID transactionId);

    List<TransactionStatusEvent> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
            LocalDateTime from, LocalDateTime to
    );

    long countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(LocalDateTime from, LocalDateTime to);
}
