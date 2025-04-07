package bumblebee.xchangepass.domain.exchangeRate.repository.exchangeTemp;

import bumblebee.xchangepass.domain.exchangeRate.entity.ExchangeRate;

import java.util.List;

public interface ExchangeRepositoryCustom  {

    void renameTable(String oldTableName, String newTableName);

    void createTempTable();

    boolean isTableExist(String tableName);

    void dropTableIfExists(String tableName);

    List<ExchangeRate> findByBaseCurrencyAndKey(String base, String targetKey);
}
