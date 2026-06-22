package bumblebee.xchangepass.domain.wallet.transfer.recovery;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransaction;
import bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType;
import bumblebee.xchangepass.domain.wallet.transaction.repository.WalletTransactionRepository;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransfer;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferFailureStage;
import bumblebee.xchangepass.domain.wallet.transfer.entity.WalletTransferStatus;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.repository.WalletTransferRecoveryCaseRepository;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseSeverity;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.entity.WalletTransferRecoveryCaseType;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferRecoveryCaseService;
import bumblebee.xchangepass.domain.wallet.transfer.recovery.service.WalletTransferStaleFinalizer;
import bumblebee.xchangepass.domain.wallet.transfer.repository.WalletTransferRepository;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferLifecycleService;
import bumblebee.xchangepass.domain.wallet.transfer.service.WalletTransferReservationWriter;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "wallet.transfer.recovery.enabled=false")
@Testcontainers
@ActiveProfiles("test")
class WalletTransferStaleFinalizerIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("xcp_recovery_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WalletTransferReservationWriter reservationWriter;
    @Autowired
    private WalletTransferLifecycleService lifecycleService;
    @Autowired
    private WalletTransferStaleFinalizer finalizer;
    @Autowired
    private WalletTransferRepository transferRepository;
    @Autowired
    private WalletTransferRecoveryCaseRepository caseRepository;
    @Autowired
    private WalletTransferRecoveryCaseService caseService;
    @Autowired
    private WalletTransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        caseRepository.deleteAll();
        transactionRepository.deleteAll();
        transferRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void finalizesStaleRequestedTransferWithNoLedger() {
        WalletTransfer transfer = staleTransfer(false);

        boolean finalized = finalizer.finalizeIfUnchanged(
                transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff()
        );

        assertThat(finalized).isTrue();
        assertFinalized(transfer.getTransferId());
    }

    @Test
    void finalizesStaleValidatingTransferWithNoLedger() {
        WalletTransfer transfer = staleTransfer(true);

        boolean finalized = finalizer.finalizeIfUnchanged(
                transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff()
        );

        assertThat(finalized).isTrue();
        assertFinalized(transfer.getTransferId());
    }

    @Test
    void doesNotFinalizeRecentOrVersionChangedTransfer() {
        WalletTransfer recent = reservationWriter.create(1L, UUID.randomUUID(), "a".repeat(64));

        assertThat(finalizer.finalizeIfUnchanged(
                recent.getTransferId(), recent.getStatus(), recent.getVersion(), cutoff()
        )).isFalse();

        WalletTransfer stale = staleTransfer(false);
        assertThat(finalizer.finalizeIfUnchanged(
                stale.getTransferId(), stale.getStatus(), stale.getVersion() + 1, cutoff()
        )).isFalse();
        assertThat(transferRepository.findById(stale.getTransferId()).orElseThrow().getStatus())
                .isEqualTo(WalletTransferStatus.REQUESTED);
    }

    @Test
    void doesNotFinalizeWhenLedgerExists() {
        User user = userRepository.save(new User(
                "recovery@example.com", "Password123!", "복구", "recovery-user",
                "010-9999-0000", Sex.MALE, new BCryptPasswordEncoder()
        ));
        WalletTransfer transfer = staleTransfer(false);
        transactionRepository.saveAndFlush(new WalletTransaction(
                user, null, BigDecimal.ONE, BigDecimal.ONE,
                "KRW", "KRW", WalletTransactionType.TRANSFER,
                LocalDateTime.now(), transfer.getTransferId()
        ));

        boolean finalized = finalizer.finalizeIfUnchanged(
                transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff()
        );

        assertThat(finalized).isFalse();
        assertThat(transferRepository.findById(transfer.getTransferId()).orElseThrow().getStatus())
                .isEqualTo(WalletTransferStatus.REQUESTED);
    }

    @Test
    void concurrentFinalizersChangeStaleTransferOnlyOnce() throws Exception {
        WalletTransfer transfer = staleTransfer(true);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Future<Boolean> first = executor.submit(() -> {
            start.await();
            return finalizer.finalizeIfUnchanged(
                    transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff()
            );
        });
        Future<Boolean> second = executor.submit(() -> {
            start.await();
            return finalizer.finalizeIfUnchanged(
                    transfer.getTransferId(), transfer.getStatus(), transfer.getVersion(), cutoff()
            );
        });

        start.countDown();
        assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertFinalized(transfer.getTransferId());
    }

    @Test
    void concurrentDetectionsKeepOneRecoveryCaseAndCountEveryDetection() throws Exception {
        WalletTransfer transfer = staleTransfer(true);
        int detections = 10;
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(detections);
        var futures = java.util.stream.IntStream.range(0, detections)
                .mapToObj(index -> executor.submit(() -> {
                    start.await();
                    caseService.record(
                            transfer, 0L,
                            WalletTransferRecoveryCaseType.AMBIGUOUS_PROCESSING,
                            WalletTransferRecoveryCaseSeverity.WARNING
                    );
                    return null;
                }))
                .toList();

        start.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(caseRepository.findAll()).singleElement().satisfies(recoveryCase -> {
            assertThat(recoveryCase.getTransferId()).isEqualTo(transfer.getTransferId());
            assertThat(recoveryCase.getDetectionCount()).isEqualTo(detections);
        });
    }

    private WalletTransfer staleTransfer(boolean validating) {
        WalletTransfer transfer = reservationWriter.create(1L, UUID.randomUUID(), "a".repeat(64));
        if (validating) {
            lifecycleService.startValidating(transfer.getTransferId());
        }
        jdbcTemplate.update(
                "update wallet_transfer_request set updated_at = ? where transfer_id = ?",
                LocalDateTime.now().minusMinutes(10), transfer.getTransferId()
        );
        return transferRepository.findById(transfer.getTransferId()).orElseThrow();
    }

    private LocalDateTime cutoff() {
        return LocalDateTime.now().minusMinutes(5);
    }

    private void assertFinalized(UUID transferId) {
        WalletTransfer finalized = transferRepository.findById(transferId).orElseThrow();
        assertThat(finalized.getStatus()).isEqualTo(WalletTransferStatus.FAILED);
        assertThat(finalized.getFailureCode()).isEqualTo(ErrorCode.TRANSACTION_STALE.name());
        assertThat(finalized.getFailureStage()).isEqualTo(WalletTransferFailureStage.RECOVERY_TIMEOUT);
        assertThat(finalized.getRetryable()).isTrue();
        assertThat(finalized.getFailedAt()).isNotNull();
    }
}
