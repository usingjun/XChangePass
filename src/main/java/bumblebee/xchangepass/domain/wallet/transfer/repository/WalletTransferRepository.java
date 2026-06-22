package bumblebee.xchangepass.domain.wallet.transfer.repository;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransferRepository extends JpaRepository<WalletTransfer, UUID> {

    Optional<WalletTransfer> findBySenderUserIdAndIdempotencyKey(Long senderUserId, UUID idempotencyKey);

    Optional<WalletTransfer> findByTransferIdAndSenderUserId(UUID transferId, Long senderUserId);

    List<WalletTransfer> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<WalletTransferStatus> statuses, LocalDateTime updatedBefore
    );

    List<WalletTransfer> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<WalletTransferStatus> statuses, LocalDateTime updatedBefore, Pageable pageable
    );

    @Query("""
            select transfer
            from WalletTransfer transfer
            where transfer.status in :statuses
              and transfer.updatedAt < :cutoff
              and not exists (
                  select recoveryCase.caseId
                  from WalletTransferRecoveryCase recoveryCase
                  where recoveryCase.transferId = transfer.transferId
                    and recoveryCase.caseStatus <> bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus.RESOLVED
              )
            order by transfer.updatedAt asc
            """)
    List<WalletTransfer> findUnresolvedRecoveryCandidates(
            @Param("statuses") Collection<WalletTransferStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    List<WalletTransfer> findByStatusIn(Collection<WalletTransferStatus> statuses, Pageable pageable);

    @Modifying
    @Query(value = """
            UPDATE wallet_transfer_request transfer
            SET status = 'FAILED',
                failure_code = 'TRANSACTION_STALE',
                failure_stage = 'RECOVERY_TIMEOUT',
                retryable = true,
                failed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE transfer.transfer_id = :transferId
              AND transfer.status = :observedStatus
              AND transfer.version = :observedVersion
              AND transfer.updated_at < :cutoff
              AND transfer.status IN ('REQUESTED', 'VALIDATING')
              AND NOT EXISTS (
                  SELECT 1
                  FROM wallet_transaction ledger
                  WHERE ledger.transfer_id = transfer.transfer_id
              )
            """, nativeQuery = true)
    int finalizeStaleTransfer(@Param("transferId") UUID transferId,
                              @Param("observedStatus") String observedStatus,
                              @Param("observedVersion") Long observedVersion,
                              @Param("cutoff") LocalDateTime cutoff);
}
