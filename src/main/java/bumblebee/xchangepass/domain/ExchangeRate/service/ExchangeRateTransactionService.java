package bumblebee.xchangepass.domain.ExchangeRate.service;

import bumblebee.xchangepass.domain.ExchangeRate.repository.ExchangeRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeRateTransactionService {

    public final ExchangeRepository exchangeRepository;

    public final EntityManager entityManager;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void swapExchangeRateTables() {
        try {
            boolean isMainTableExist = exchangeRepository.isTableExist("exchange_rate");
            boolean isTempTableExist = exchangeRepository.isTableExist("exchange_rate_temp");

            if (isMainTableExist) {
                exchangeRepository.renameTable("exchange_rate", "exchange_rate_old");
            }
            if (isTempTableExist) {
                exchangeRepository.renameTable("exchange_rate_temp", "exchange_rate");
            }

            // 기존 테이블 삭제
            if (exchangeRepository.isTableExist("exchange_rate_old")) {
                exchangeRepository.dropTableIfExists("exchange_rate_old");
            }

            // 새로운 임시 테이블 생성
            if (!exchangeRepository.isTableExist("exchange_rate_temp")) {
                exchangeRepository.createTempTable();
                addIndexToExchangeRateTable();
            }
        } catch (DataAccessException e) {
            throw ErrorCode.EXCHANGE_DATA_ACCESS_EXCEPTION.commonException();
        } catch (CommonException e){
            throw ErrorCode.EXCHANGE_SQL_EXECUTION_ERROR.commonException();
        }
    }



    public void addIndexToExchangeRateTable() {
        String checkIndexSql = "SELECT 1 FROM pg_indexes WHERE tablename = 'exchange_rate' AND indexname = 'exchange_rate_jsonb_idx';";
        Query checkIndexQuery = entityManager.createNativeQuery(checkIndexSql);
        List<?> result = checkIndexQuery.getResultList();

        if (result.isEmpty()) {
            String createIndexSql = "CREATE INDEX IF NOT EXISTS exchange_rate_jsonb_idx ON exchange_rate USING GIN (exchange_rates);";
            entityManager.createNativeQuery(createIndexSql).executeUpdate();
        }
    }


}
