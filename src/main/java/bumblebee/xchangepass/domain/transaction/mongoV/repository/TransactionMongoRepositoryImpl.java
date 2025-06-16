package bumblebee.xchangepass.domain.transaction.mongoV.repository;

import bumblebee.xchangepass.domain.transaction.mongoV.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.mongoV.entity.TransactionDocument;
import bumblebee.xchangepass.domain.transaction.rdbmsV.dto.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.rdbmsV.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@RequiredArgsConstructor
public class TransactionMongoRepositoryImpl implements TransactionMongoRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<TransactionResponse> search(Long userId, TransactionSearchCondition cond, int size) {
        Query query = new Query();

        if (userId != null) {
            Criteria userCriteria = new Criteria().orOperator(
                    Criteria.where("userId").is(userId),
                    new Criteria().andOperator(
                            Criteria.where("type").is(TransactionType.WALLET.name()),
                            Criteria.where("metadata.receiver").is(userId)
                    )
            );
            query.addCriteria(userCriteria);
        }

        if (cond.transactionType() != null) {
            query.addCriteria(Criteria.where("type").is(cond.transactionType().name()));
        }

        buildTransactionTimeCriteria(cond, query);

        query.with(Sort.by(Sort.Direction.DESC, "transactionTime"));
        query.limit(size);

        List<TransactionDocument> docs = mongoTemplate.find(query, TransactionDocument.class);


        return null;
//        return docs.stream()
//                .map(d -> new TransactionResponse(
//                        d.getUserId(),
//                        d.getTransactionTime(),
//                        TransactionType.valueOf(d.getType()),
//                        mapToDto(d.getType(), d.getMetadata())
//                ))
//                .toList();
    }

    private void buildTransactionTimeCriteria(TransactionSearchCondition cond, Query query) {
        if (cond.cursor() != null) {
            // 커서 우선 적용
            query.addCriteria(Criteria.where("transactionTime").lt(cond.cursor()));
            return;
        }

        // 커서 없으면 기간 조건 적용
        Criteria criteria = new Criteria();

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
