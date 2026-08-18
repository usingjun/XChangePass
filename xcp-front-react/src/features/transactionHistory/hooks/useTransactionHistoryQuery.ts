import { useInfiniteQuery } from '@tanstack/react-query'
import { fetchTransactionHistory } from '../api/transactionHistoryApi'
import type { TransactionSearchCondition } from '../types'

// 서버 size 상한(1~100)에 맞춰 매 페이지 100건씩 요청한다.
const PAGE_SIZE = 100

export function useTransactionHistoryQuery(condition: TransactionSearchCondition = {}) {
  return useInfiniteQuery({
    queryKey: ['transactionHistory', condition],
    queryFn: ({ pageParam }) =>
      fetchTransactionHistory({ ...condition, cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  })
}
