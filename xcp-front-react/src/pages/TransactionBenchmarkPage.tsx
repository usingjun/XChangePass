import { useMemo, useState } from 'react'
import TransactionTable from '../features/transactionHistory/components/TransactionTable'
import { generateTransactionFixtures } from '../features/transactionHistory/fixtures/generateTransactionFixtures'

// 개발 전용 페이지. production 라우팅에서 제외된다(router/index.tsx의 import.meta.env.DEV 분기).
// 책임: fixture 데이터 볼륨과 virtualized 여부를 UI 상태로 들고, 실제 화면(TransactionHistoryPage)과
// 동일한 TransactionTable/columns를 그대로 재사용해 naive/가상화 렌더를 같은 컴포넌트 트리에서 비교한다.
const VOLUMES = [1_000, 10_000, 30_000, 100_000] as const
type Volume = (typeof VOLUMES)[number]

function TransactionBenchmarkPage() {
  const [volume, setVolume] = useState<Volume>(1_000)
  const [virtualized, setVirtualized] = useState(false)

  // fixtures는 서버 상태가 아니라 이 페이지의 UI 선택(volume)에서 파생된 값이지만,
  // volume이 바뀌지 않는 한 매 렌더(예: virtualized 토글)마다 최대 10만 건을 다시
  // 생성할 이유가 없어 useMemo로 volume에만 의존시킨다.
  const fixtures = useMemo(() => generateTransactionFixtures(volume, 1), [volume])

  return (
    <main className="transaction-benchmark-page">
      <h1>거래내역 렌더링 벤치마크 (개발 전용)</h1>
      <p>실제 API를 호출하지 않는다. seed 기반으로 생성한 고정 데이터로 대량 렌더링을 재현한다.</p>
      <div className="transaction-benchmark-page__controls">
        <label>
          데이터 볼륨{' '}
          <select
            value={volume}
            onChange={(event) => setVolume(Number(event.target.value) as Volume)}
          >
            {VOLUMES.map((value) => (
              <option key={value} value={value}>
                {value.toLocaleString('ko-KR')}건
              </option>
            ))}
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            checked={virtualized}
            onChange={(event) => setVirtualized(event.target.checked)}
          />{' '}
          가상화
        </label>
      </div>
      <TransactionTable data={fixtures} virtualized={virtualized} />
    </main>
  )
}

export default TransactionBenchmarkPage
