package bumblebee.xchangepass.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(TransactionStatisticsReplicaProperties.class)
public class TransactionStatisticsReplicaJdbcConfig {

    @Bean(name = "transactionStatisticsReplicaJdbcTemplate")
    @ConditionalOnProperty(prefix = "transaction.statistics.replica", name = "enabled", havingValue = "true")
    public JdbcTemplate transactionStatisticsReplicaJdbcTemplate(
            TransactionStatisticsReplicaProperties properties
    ) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("transaction.statistics.replica.url is required when replica is enabled");
        }
        return new JdbcTemplate(replicaDataSource(properties));
    }

    private DataSource replicaDataSource(TransactionStatisticsReplicaProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }
}
