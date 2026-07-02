package bumblebee.xchangepass.domain.wallet.reconciliation;

import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssue;
import bumblebee.xchangepass.domain.wallet.reconciliation.entity.WalletBalanceReconciliationIssueStatus;
import bumblebee.xchangepass.domain.wallet.reconciliation.repository.WalletBalanceReconciliationIssueRepository;
import bumblebee.xchangepass.global.config.QueryDSLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QueryDSLConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletBalanceReconciliationIssueMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_reconciliation_migration_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private WalletBalanceReconciliationIssueRepository issueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        issueRepository.deleteAll();
    }

    @Test
    void flywayMigrationCreatesPartialUniqueIndex() {
        String indexName = jdbcTemplate.queryForObject(
                "select indexname from pg_indexes where indexname = 'ux_wallet_balance_reconciliation_issue_open'",
                String.class
        );

        assertThat(indexName).isEqualTo("ux_wallet_balance_reconciliation_issue_open");
    }

    @Test
    void duplicateOpenIssueForSameWalletAndCurrencyIsRejectedByDatabase() {
        issueRepository.saveAndFlush(issue(10L, "KRW"));

        assertThatThrownBy(() -> issueRepository.saveAndFlush(issue(10L, "KRW")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resolvedIssueDoesNotBlockNewOpenIssueForSameWalletAndCurrency() {
        WalletBalanceReconciliationIssue resolved = issue(10L, "KRW");
        resolved.resolve();
        issueRepository.saveAndFlush(resolved);

        issueRepository.saveAndFlush(issue(10L, "KRW"));

        assertThat(issueRepository.findAll()).hasSize(2)
                .extracting(WalletBalanceReconciliationIssue::getStatus)
                .containsExactlyInAnyOrder(
                        WalletBalanceReconciliationIssueStatus.RESOLVED,
                        WalletBalanceReconciliationIssueStatus.OPEN
                );
    }

    @Test
    void differentCurrenciesCanHaveSeparateOpenIssues() {
        issueRepository.saveAndFlush(issue(10L, "KRW"));
        issueRepository.saveAndFlush(issue(10L, "USD"));

        assertThat(issueRepository.findAll()).hasSize(2);
    }

    @Test
    void differentWalletsCanHaveSeparateOpenIssues() {
        issueRepository.saveAndFlush(issue(10L, "KRW"));
        issueRepository.saveAndFlush(issue(20L, "KRW"));

        assertThat(issueRepository.findAll()).hasSize(2);
    }

    private WalletBalanceReconciliationIssue issue(Long walletId, String currency) {
        return new WalletBalanceReconciliationIssue(
                walletId,
                walletId * 10,
                currency,
                amount("1200.00"),
                amount("1000.00"),
                amount("200.00"),
                LocalDateTime.now()
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
