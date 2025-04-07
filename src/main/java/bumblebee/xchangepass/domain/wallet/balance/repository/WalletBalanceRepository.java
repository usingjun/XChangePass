package bumblebee.xchangepass.domain.wallet.balance.repository;

import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {

    @Query("""
                select wb
                from WalletBalance wb
                where wb.wallet.walletId=:walletId
            """)
    List<WalletBalance> findByWalletId(@Param("walletId") Long walletId);

    @Query("""
            select wb
            from WalletBalance wb
            where wb.wallet.walletId=:walletId and wb.currency=:currency
            """)
    Optional<WalletBalance> findByWalletIdAndCurrency(@Param("walletId") Long walletId, @Param("currency") Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select wb
                from WalletBalance wb
                where wb.wallet.walletId=:walletId
            """)
    List<WalletBalance> findByWalletIdWithPessimisticLock(@Param("walletId") final Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wb FROM WalletBalance wb WHERE wb.wallet.walletId = :walletId AND wb.currency = :currency")
    Optional<WalletBalance> findByWalletIdAndCurrencyWithPessimisticLock(@Param("walletId") Long walletId, @Param("currency") Currency currency);

    @Query("SELECT COUNT(wb) > 0 FROM WalletBalance wb WHERE wb.wallet.walletId = :walletId and wb.currency=:currency")
    boolean existsByCurrency(@Param("walletId") Long walletId, @Param("currency") Currency currency);


    //테스트용
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update WalletBalance wb set wb.balance=0 where wb.wallet.walletId=:walletId and wb.currency=:currency")
    void zeroBalance(@Param("walletId") Long walletId, @Param("currency") Currency currency);
}
