import { createBrowserRouter } from 'react-router-dom'
import HomePage from '../pages/HomePage'
import TransactionHistoryPage from '../pages/TransactionHistoryPage'

// 벤치마크 라우트는 기본적으로 개발 전용이다. import.meta.env.DEV/VITE_ENABLE_BENCHMARK는
// 빌드 시 정적으로 치환되므로, 일반 `npm run build`에서는 이 배열이 빈 배열로 접혀
// TransactionBenchmarkPage/fixtures 코드가 번들에 포함되지 않는다. lazy + 동적 import로
// 코드 스플리팅까지 명시적으로 분리해둔다.
//
// production 빌드로 실제 렌더링 성능을 측정해야 할 때만 `npm run build:benchmark`
// (VITE_ENABLE_BENCHMARK=true, --mode benchmark)로 이 라우트를 포함한 별도 빌드를 만든다.
// 이 조건은 HomePage.tsx에도 동일하게(따로) 써 있다 — 공용 상수로 추출하면 import 체인을
// 거치면서 이 파일 안에서 esbuild가 바로 상수로 접지 못해 트리셰이킹이 깨질 수 있어 일부러 중복했다.
const devRoutes =
  import.meta.env.DEV || import.meta.env.VITE_ENABLE_BENCHMARK === 'true'
    ? [
        {
          path: '/dev/transaction-benchmark',
          lazy: async () => {
            const { default: Component } = await import('../pages/TransactionBenchmarkPage')
            return { Component }
          },
        },
      ]
    : []

export const router = createBrowserRouter([
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '/transactions',
    element: <TransactionHistoryPage />,
  },
  ...devRoutes,
])
