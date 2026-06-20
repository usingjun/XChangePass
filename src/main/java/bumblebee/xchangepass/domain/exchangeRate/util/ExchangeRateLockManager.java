package bumblebee.xchangepass.domain.exchangeRate.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;

import static bumblebee.xchangepass.global.common.Constants.EXCHANGE_RATE_LOCK_KEY;

@Component
@RequiredArgsConstructor
public class ExchangeRateLockManager {

    @PersistenceContext
    private final EntityManager entityManager;



    public boolean tryAcquireLock() {
        Query query = entityManager.createNativeQuery("SELECT pg_try_advisory_lock(:lockKey)");
        query.setParameter("lockKey", EXCHANGE_RATE_LOCK_KEY);
        Boolean result = (Boolean) query.getSingleResult();
        return result != null && result;
    }

    public void releaseLock() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                stmt.setLong(1, EXCHANGE_RATE_LOCK_KEY);
                stmt.executeQuery();
            }
        });
    }
}
