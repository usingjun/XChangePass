import { Link } from 'react-router-dom'

function HomePage() {
  return (
    <main>
      <h1>xcp-front-react</h1>
      <nav>
        <ul>
          <li>
            <Link to="/transactions">거래내역</Link>
          </li>
          {/* router/index.tsx와 동일한 조건 — 공용 상수로 추출하지 않은 이유는 그쪽 주석 참고 */}
          {(import.meta.env.DEV || import.meta.env.VITE_ENABLE_BENCHMARK === 'true') && (
            <li>
              <Link to="/dev/transaction-benchmark">거래내역 렌더링 벤치마크 (개발/측정 전용)</Link>
            </li>
          )}
        </ul>
      </nav>
    </main>
  )
}

export default HomePage
