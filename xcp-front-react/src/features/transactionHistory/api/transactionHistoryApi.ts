import { apiClient } from '../../../lib/apiClient'
import type { TransactionPageResponse, TransactionSearchCondition } from '../types'

export interface TransactionHistoryQueryParams extends TransactionSearchCondition {
  // 서버가 이전 응답에서 준 nextCursor를 그대로 echo한다. 첫 페이지는 null.
  cursor: string | null
  size: number
}

// axios는 2xx 응답이면 body가 기대한 JSON 모양인지는 검증하지 않고 그대로 넘긴다.
// 인증 실패로 리버스 프록시/SPA 폴백이 대신 HTML을 200으로 돌려주는 등, "성공했지만
// 우리가 기대한 형태가 아닌" 응답이 오면 여기서 걸러 명시적으로 실패시켜야
// TransactionTable이 undefined 행을 렌더링하다 죽는 대신 쿼리 에러 상태로 이어진다.
function assertTransactionPageResponse(
  data: unknown,
): asserts data is TransactionPageResponse {
  const isValid =
    typeof data === 'object' && data !== null && Array.isArray((data as { items?: unknown }).items)

  if (!isValid) {
    throw new Error('거래내역 응답 형식이 올바르지 않습니다.')
  }
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
  assertTransactionPageResponse(data)
  return data
}
