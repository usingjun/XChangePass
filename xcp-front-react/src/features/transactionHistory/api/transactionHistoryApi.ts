import { apiClient } from '../../../lib/apiClient'
import type { TransactionPageResponse, TransactionSearchCondition } from '../types'

export interface TransactionHistoryQueryParams extends TransactionSearchCondition {
  // 서버가 이전 응답에서 준 nextCursor를 그대로 echo한다. 첫 페이지는 null.
  cursor: string | null
  size: number
}

export async function fetchTransactionHistory(
  params: TransactionHistoryQueryParams,
): Promise<TransactionPageResponse> {
  const { cursor, ...rest } = params
  const { data } = await apiClient.get<TransactionPageResponse>('/v2/transaction', {
    params: {
      ...rest,
      ...(cursor ? { cursor } : {}),
    },
  })
  return data
}
