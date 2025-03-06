package bumblebee.xchangepass.domain.walletBalance.repository;

import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
    List<WalletBalance> findByWalletId(Long walletId);

    @Query("""
            select wb
            from WalletBalance wb
            where wb.wallet.walletId=:walletId and wb.currency=:currency
            """)
    WalletBalance findByWalletIdAndCurrency(Long walletId, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select wb
                from WalletBalance wb
                where wb.wallet.walletId=:walletId
            """)
    Optional<List<WalletBalance>> findByWalletIdWithPessimisticLock(final Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 🔥 비관적 락 적용
    @Query("SELECT wb FROM WalletBalance wb WHERE wb.wallet.walletId = :walletId AND wb.currency = :currency")
    Optional<WalletBalance> findByWalletIdAndCurrencyWithPessimisticLock(@Param("walletId") Long walletId, @Param("currency") Currency currency);

    @Query("SELECT COUNT(wb) > 0 FROM WalletBalance wb WHERE wb.wallet.walletId = :walletId and wb.currency=:currency")
    boolean existsByCurrency(Long walletId, Currency currency);
}
