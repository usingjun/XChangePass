package bumblebee.xchangepass.domain.ExchangeRate.repository;

import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRateTemp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateTempRepository extends JpaRepository<ExchangeRateTemp, Long> {

}
