package bumblebee.xchangepass.domain.wallet.repository;

import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
//@Transactional(readOnly = true)
public interface WalletRepository extends JpaRepository<Wallet, Long> {

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Transactional(readOnly = false)
    @Query("SELECT w FROM Wallet w WHERE w.user.userId = :userId")
    Wallet findByUserId(Long userId);

    @Query("SELECT COUNT(w) > 0 FROM Wallet w WHERE w.user.userId = :userId")
    boolean existsByUserId(Long userId);

}
