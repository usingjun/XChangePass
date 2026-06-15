package bumblebee.xchangepass.domain.cardTransaction.service;

import bumblebee.xchangepass.domain.cardTransaction.dto.request.PaymentApprovedEvent;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import bumblebee.xchangepass.domain.cardTransaction.repository.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardTransactionService {

    private final CardTransactionRepository transactionRepository;

    @Transactional
    public void handlePaymentApprovedEvent(PaymentApprovedEvent event) {

        transactionRepository.save(new CardTransaction(
                event.user(),
                event.merchantName(),
                event.approvedAmount(),
                event.approvedCurrency().getCurrencyCode(),
                event.krwAmount(),
                event.balanceAfter(),
                event.approvalNumber(),
                event.cardTransactionType(),
                event.transactionTime()
        ));

        log.info("Card transaction saved. approvalNumber={}", event.approvalNumber());
    }

}
