package bumblebee.xchangepass.domain.transaction.repository;

import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

import static bumblebee.xchangepass.domain.transaction.mapper.TransactionMetadataMapper.mapToDto;

@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size) {
        Query query = new Query();

        if (cond.transactionType() == TransactionType.WALLET) {
            // sender 또는 receiver 둘 다 포함
            Criteria senderCriteria = Criteria.where("userId").is(userId);
            Criteria receiverCriteria = Criteria.where("metadata.receiver").is(userId);

            // 둘 중 하나라도 만족하면 포함
            query.addCriteria(new Criteria().andOperator(
                    Criteria.where("metadata.type").is(TransactionType.WALLET.name()),
                    new Criteria().orOperator(senderCriteria, receiverCriteria)
            ));
        } else {
            // 기본 조건
            List<Criteria> criteriaList = new ArrayList<>();
            criteriaList.add(Criteria.where("userId").is(userId));
            if (cond.transactionType() != null)
                criteriaList.add(Criteria.where("metadata.type").is(cond.transactionType().name()));

            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        if (cond.cardTransactionType() != null) {
            query.addCriteria(Criteria.where("metadata.cardType").is(cond.cardTransactionType().name()));
        }

        if (cond.walletTransactionType() != null) {
            query.addCriteria(Criteria.where("metadata.walletType").is(cond.walletTransactionType().name()));
        }

        buildTransactionTimeCriteria(cond, query);

        query.with(Sort.by(Sort.Direction.DESC, "transactionTime"));
        query.limit(size);

        List<TransactionDocument> docs = mongoTemplate.find(query, TransactionDocument.class);

        return docs.stream()
                .map(d -> new TransactionResponse(
                        d.getUserId(),
                        d.getBeforeCurrency(),
                        d.getAfterCurrency(),
                        d.getTransactionTime(),
                        mapToDto(d.getMetadata())
                ))
                .toList();
    }

    private void buildTransactionTimeCriteria(TransactionSearchCondition cond, Query query) {
        if (cond.cursor() != null) {
            // 커서 우선 적용
            query.addCriteria(Criteria.where("transactionTime").lt(cond.cursor()));
            return;
        }

        if (cond.startDate() != null || cond.endDate() != null) {
            Criteria timeCriteria = Criteria.where("transactionTime");

            if (cond.startDate() != null && cond.endDate() != null) {
                timeCriteria = timeCriteria.gte(cond.startDate()).lte(cond.endDate());
            } else if (cond.startDate() != null) {
                timeCriteria = timeCriteria.gte(cond.startDate());
            } else if (cond.endDate() != null) {
                timeCriteria = timeCriteria.lte(cond.endDate());
            }

            query.addCriteria(timeCriteria);
        }
    }

}
