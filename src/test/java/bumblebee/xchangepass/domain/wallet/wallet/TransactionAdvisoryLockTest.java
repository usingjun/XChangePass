package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.wallet.wallet.service.TransactionAdvisoryLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import static bumblebee.xchangepass.global.common.Constants.EXCHANGE_RATE_LOCK_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransactionAdvisoryLockTest {

    @Test
    void walletLocksUsePositiveKeysAndSystemLockUsesNegativeKeySpace() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:walletId)"))
                .thenReturn(query);
        when(query.setParameter("walletId", 10L)).thenReturn(query);
        TransactionAdvisoryLock advisoryLock = new TransactionAdvisoryLock(entityManager);

        advisoryLock.acquire(10L);

        verify(query).getSingleResult();
        assertThat(EXCHANGE_RATE_LOCK_KEY).isNegative();
    }

    @Test
    void rejectsKeysReservedForSystemLocks() {
        TransactionAdvisoryLock advisoryLock = new TransactionAdvisoryLock(mock(EntityManager.class));

        assertThatThrownBy(() -> advisoryLock.acquire(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> advisoryLock.acquire(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
