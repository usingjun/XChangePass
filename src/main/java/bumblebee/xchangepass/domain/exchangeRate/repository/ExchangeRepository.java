package bumblebee.xchangepass.domain.exchangeRate.repository;


import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;
import bumblebee.xchangepass.domain.exchangeRate.repository.exchangeTemp.ExchangeRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeRepository extends JpaRepository<ExchangeRate, Long> , ExchangeRepositoryCustom {
    List<ExchangeRate> findByBaseCurrency(String baseCurrency);
}
