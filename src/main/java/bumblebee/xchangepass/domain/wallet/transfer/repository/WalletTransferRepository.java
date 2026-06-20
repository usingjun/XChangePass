package bumblebee.xchangepass.domain.wallet.transfer.repository;

import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletTransferRepository extends JpaRepository<WalletTransfer, UUID> {

    Optional<WalletTransfer> findBySenderUserIdAndIdempotencyKey(Long senderUserId, UUID idempotencyKey);
}
