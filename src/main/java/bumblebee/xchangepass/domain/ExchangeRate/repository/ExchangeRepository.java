package bumblebee.xchangepass.domain.ExchangeRate.repository;


import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.ExchangeRate.repository.exchangeTemp.ExchangeRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRepository extends JpaRepository<ExchangeRate, Long> , ExchangeRepositoryCustom {
    List<ExchangeRate> findByBaseCurrency(String baseCurrency);
}
