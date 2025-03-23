package bumblebee.xchangepass.domain.wallet.wallet.repository;

import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NamedLockRepository extends JpaRepository<Wallet, Long> {

    @Query(value = "SELECT pg_advisory_lock(:key)", nativeQuery = true)
    void getLock(@Param("key") Long key);

    @Query(value = "SELECT pg_advisory_unlock(:key)", nativeQuery = true)
    Boolean releaseLock(@Param("key") Long key);
}
