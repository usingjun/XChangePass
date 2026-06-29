package bumblebee.xchangepass.domain.wallet.transfer.recovery.repository;

import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransferRecoveryCaseRepository
        extends JpaRepository<WalletTransferRecoveryCase, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recoveryCase
            from WalletTransferRecoveryCase recoveryCase
            where recoveryCase.transferId = :transferId
              and recoveryCase.caseType = :caseType
            """)
    Optional<WalletTransferRecoveryCase> findForUpdate(@Param("transferId") UUID transferId,
                                                       @Param("caseType") WalletTransferRecoveryCaseType caseType);

    @Query("""
            select recoveryCase
            from WalletTransferRecoveryCase recoveryCase
            where recoveryCase.caseId = :caseId
            """)
    Optional<WalletTransferRecoveryCase> findByIdForOperation(@Param("caseId") UUID caseId);

    long countByCaseStatusNotAndCaseTypeAndSeverity(
            WalletTransferRecoveryCaseStatus excludedStatus,
            WalletTransferRecoveryCaseType caseType,
            WalletTransferRecoveryCaseSeverity severity
    );

    @Query("""
            select recoveryCase
            from WalletTransferRecoveryCase recoveryCase
            where (:caseType is null or recoveryCase.caseType = :caseType)
              and (:severity is null or recoveryCase.severity = :severity)
              and (:caseStatus is null or recoveryCase.caseStatus = :caseStatus)
              and (:transferId is null or recoveryCase.transferId = :transferId)
              and (:fromDetectedAt is null or recoveryCase.lastDetectedAt >= :fromDetectedAt)
              and (:toDetectedAt is null or recoveryCase.lastDetectedAt < :toDetectedAt)
            order by recoveryCase.lastDetectedAt desc, recoveryCase.firstDetectedAt desc
            """)
    List<WalletTransferRecoveryCase> findForOperations(
            @Param("caseType") WalletTransferRecoveryCaseType caseType,
            @Param("severity") WalletTransferRecoveryCaseSeverity severity,
            @Param("caseStatus") WalletTransferRecoveryCaseStatus caseStatus,
            @Param("transferId") UUID transferId,
            @Param("fromDetectedAt") LocalDateTime fromDetectedAt,
            @Param("toDetectedAt") LocalDateTime toDetectedAt,
            Pageable pageable
    );
}
