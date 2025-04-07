package bumblebee.xchangepass.domain.wallet.wallet.repository;

import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {

    List<ScheduledTransfer> findByStatusAndScheduledAtBefore(WalletTransferStatus status, LocalDateTime time);
}
