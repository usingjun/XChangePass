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
          {import.meta.env.DEV && (
            <li>
              <Link to="/dev/transaction-benchmark">거래내역 렌더링 벤치마크 (개발 전용)</Link>
            </li>
          )}
        </ul>
      </nav>
    </main>
  )
}

export default HomePage
