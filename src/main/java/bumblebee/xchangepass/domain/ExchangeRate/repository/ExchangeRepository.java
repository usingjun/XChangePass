package bumblebee.xchangepass.domain.ExchangeRate.repository;


import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExchangeRepository extends JpaRepository<ExchangeRate, Long> {


    @Query("SELECT e FROM ExchangeRate e WHERE e.baseCurrency = :baseCurrency AND e.updatedAt >= :cacheThreshold")
    List<ExchangeRate> findValidRates(@Param("baseCurrency") String baseCurrency, @Param("cacheThreshold") LocalDateTime cacheThreshold);

    List<ExchangeRate> findByBaseCurrency(String baseCurrency);

}
