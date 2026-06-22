package bumblebee.xchangepass.domain.wallet.transfer.recovery.repository;

import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCase;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByCaseStatusNotAndCaseTypeAndSeverity(
            WalletTransferRecoveryCaseStatus excludedStatus,
            WalletTransferRecoveryCaseType caseType,
            WalletTransferRecoveryCaseSeverity severity
    );
}
