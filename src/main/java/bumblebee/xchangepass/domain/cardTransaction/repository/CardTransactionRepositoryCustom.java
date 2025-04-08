package bumblebee.xchangepass.domain.cardTransaction.repository;

import bumblebee.xchangepass.domain.cardTransaction.dto.response.CardTransactionSummaryResponse;
import bumblebee.xchangepass.domain.cardTransaction.entity.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardTransactionRepositoryCustom {

    //무한 스크롤 거래내역
    List<CardTransactionSummaryResponse> getUserTransactions(Long userId, Long lastTransactionId, int size);
}
