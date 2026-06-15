package bumblebee.xchangepass.domain.wallet.wallet.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionAdvisoryLock {

    private final EntityManager entityManager;

    public void acquire(Long walletId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:walletId)")
                .setParameter("walletId", walletId)
                .getSingleResult();
    }
}
