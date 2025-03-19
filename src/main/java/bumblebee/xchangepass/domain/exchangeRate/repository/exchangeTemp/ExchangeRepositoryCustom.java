package bumblebee.xchangepass.domain.exchangeRate.repository.exchangeTemp;

public interface ExchangeRepositoryCustom  {

    void renameTable(String oldTableName, String newTableName);

    void createTempTable();

    boolean isTableExist(String tableName);

    void dropTableIfExists(String tableName);

}
