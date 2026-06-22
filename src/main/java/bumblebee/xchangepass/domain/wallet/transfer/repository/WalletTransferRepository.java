package bumblebee.xchangepass.domain.wallet.transfer.repository;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;

public interface WalletTransferRepository extends JpaRepository<WalletTransfer, UUID> {

    Optional<WalletTransfer> findBySenderUserIdAndIdempotencyKey(Long senderUserId, UUID idempotencyKey);

    Optional<WalletTransfer> findByTransferIdAndSenderUserId(UUID transferId, Long senderUserId);

    List<WalletTransfer> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<WalletTransferStatus> statuses, LocalDateTime updatedBefore
    );
}
