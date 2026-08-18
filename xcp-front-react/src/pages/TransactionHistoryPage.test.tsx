import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TransactionHistoryPage from './TransactionHistoryPage'
import { fetchTransactionHistory } from '../features/transactionHistory/api/transactionHistoryApi'
import { generateTransactionFixtures } from '../features/transactionHistory/fixtures/generateTransactionFixtures'

vi.mock('../features/transactionHistory/api/transactionHistoryApi')

const mockedFetchTransactionHistory = vi.mocked(fetchTransactionHistory)

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TransactionHistoryPage />
    </QueryClientProvider>,
  )
}

describe('TransactionHistoryPage', () => {
  it('shows a loading state, then renders fetched transactions', async () => {
    mockedFetchTransactionHistory.mockResolvedValueOnce({
      items: generateTransactionFixtures(3, 1),
      nextCursor: null,
      hasNext: false,
    })

    renderPage()

    expect(screen.getByRole('status')).toBeInTheDocument()

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument())
    expect(screen.getAllByRole('row')).toHaveLength(3 + 1)
  })

  it('shows an error state with a retry button when the request fails', async () => {
    mockedFetchTransactionHistory.mockRejectedValueOnce(new Error('네트워크 오류'))

    renderPage()

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
