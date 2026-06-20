package bumblebee.xchangepass.domain.transaction.dto.response;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> items,
        String nextCursor,
        boolean hasNext
) {
}
