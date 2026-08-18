import { createBrowserRouter } from 'react-router-dom'
import HomePage from '../pages/HomePage'
import TransactionHistoryPage from '../pages/TransactionHistoryPage'

// 벤치마크 라우트는 개발 전용이다. import.meta.env.DEV는 빌드 시 정적으로 치환되므로
// production 빌드에서는 이 배열이 빈 배열로 접혀 TransactionBenchmarkPage/fixtures 코드가
// 번들에 포함되지 않는다. lazy + 동적 import로 코드 스플리팅까지 명시적으로 분리해둔다.
const devRoutes = import.meta.env.DEV
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
