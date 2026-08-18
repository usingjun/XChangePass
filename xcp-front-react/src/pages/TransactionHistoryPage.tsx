import TransactionTable from '../features/transactionHistory/components/TransactionTable'
import { useTransactionHistoryQuery } from '../features/transactionHistory/hooks/useTransactionHistoryQuery'

// 책임: 거래내역 서버 상태(TanStack Query)를 조회해 TransactionTable에 넘기고,
// 로딩/에러 상태와 "스크롤 도달 시 다음 페이지 로드" 정책을 소유한다.
// 무한 스크롤이 "언제" 트리거되는지(스크롤 도달)는 TransactionTable이 감지해 알려주지만,
// 그 신호를 받아 실제로 fetchNextPage를 부를지 판단하는 건 서버 상태를 쥔 이 페이지의 몫이다.
function TransactionHistoryPage() {
  const {
    data,
    isPending,
    isError,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useTransactionHistoryQuery()

  if (isPending) {
    return (
      <main className="transaction-history-page">
        <h1>거래내역</h1>
        <p role="status">거래내역을 불러오는 중입니다...</p>
      </main>
    )
  }

  if (isError) {
    return (
      <main className="transaction-history-page">
        <h1>거래내역</h1>
        <p role="alert">
          거래내역을 불러오지 못했습니다: {error instanceof Error ? error.message : '알 수 없는 오류'}
        </p>
        <button type="button" onClick={() => refetch()}>
          다시 시도
        </button>
      </main>
    )
  }

  // 누적된 모든 페이지를 이어붙인다 — 이전 페이지를 버리지 않는다.
  // (가상화 없이 이 배열이 계속 커지는 것 자체가 다음 단계에서 가상화가 필요해지는 근거다.)
  const transactions = data.pages.flatMap((page) => page.items)

  const handleReachEnd = () => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage()
    }
  }

  return (
    <main className="transaction-history-page">
      <h1>거래내역</h1>
      {/* 무한스크롤로 누적되는 화면이라 데이터가 계속 커진다 — virtualized=true로 DOM 행 수를
          뷰포트 크기에 묶어둔다(6.3 가상화, docs/benchmarks/frontend-transaction-table-rendering-2026-08-18.md). */}
      <TransactionTable data={transactions} virtualized onReachEnd={handleReachEnd} />
      {isFetchingNextPage && <p role="status">다음 페이지를 불러오는 중입니다...</p>}
    </main>
  )
}

export default TransactionHistoryPage
