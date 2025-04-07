package bumblebee.xchangepass.domain.exchangeRate.repository;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRateTemp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateTempRepository extends JpaRepository<ExchangeRateTemp, Long> {

}
