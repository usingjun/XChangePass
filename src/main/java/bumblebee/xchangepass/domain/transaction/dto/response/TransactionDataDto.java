package bumblebee.xchangepass.domain.transaction.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

public interface TransactionDataDto {
    Map<String, Object> toMap();
}
