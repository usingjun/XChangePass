package bumblebee.xchangepass.global.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(TransactionStatisticsReplicaProperties.class)
public class TransactionStatisticsReplicaJdbcConfig {

    @Bean(name = "transactionStatisticsReplicaJdbcTemplate")
    @ConditionalOnProperty(prefix = "transaction.statistics.replica", name = "enabled", havingValue = "true")
    public JdbcTemplate transactionStatisticsReplicaJdbcTemplate(
            TransactionStatisticsReplicaProperties properties,
            MeterRegistry meterRegistry
    ) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("transaction.statistics.replica.url is required when replica is enabled");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setPoolName(properties.getPoolName());
        dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
        dataSource.setMinimumIdle(properties.getMinimumIdle());
        dataSource.setConnectionTimeout(properties.getConnectionTimeout());
        dataSource.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
        return new JdbcTemplate(dataSource);
    }
}
