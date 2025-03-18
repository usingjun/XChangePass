package bumblebee.xchangepass.domain.ExchangeRate.repository.exchangeTemp;

import bumblebee.xchangepass.domain.ExchangeRate.entity.ExchangeRate;

public interface ExchangeRepositoryCustom  {

    void renameTable(String oldTableName, String newTableName);

    void createTempTable();

    boolean isTableExist(String tableName);

    void dropTableIfExists(String tableName);

}
