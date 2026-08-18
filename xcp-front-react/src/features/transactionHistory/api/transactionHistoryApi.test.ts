import { describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../lib/apiClient'
import { fetchTransactionHistory } from './transactionHistoryApi'

vi.mock('../../../lib/apiClient', () => ({
  apiClient: { get: vi.fn() },
}))

const mockedGet = vi.mocked(apiClient.get)

describe('fetchTransactionHistory', () => {
  it('returns the page when the response has an items array', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { items: [], nextCursor: null, hasNext: false },
    })

    const result = await fetchTransactionHistory({ cursor: null, size: 100 })

    expect(result).toEqual({ items: [], nextCursor: null, hasNext: false })
  })

  it('throws instead of returning an HTML body silently accepted as 200 OK', async () => {
    // vite preview가 baseURL 설정이 잘못돼 /api 프록시를 못 타면 SPA index.html을
    // 200 OK로 돌려준다 — 실제로 재현했던 시나리오. axios는 성공으로 취급하지만
    // 우리는 items가 없으니 명시적으로 실패시켜야 한다.
    mockedGet.mockResolvedValueOnce({ data: '<!doctype html><html>...</html>' })

    await expect(fetchTransactionHistory({ cursor: null, size: 100 })).rejects.toThrow(
      '거래내역 응답 형식이 올바르지 않습니다.',
    )
  })

  it('throws when items is missing (e.g. an error body shaped differently)', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { errorCode: 'E0002', errorMsg: '인증되지 않은 사용자입니다.' },
    })

    await expect(fetchTransactionHistory({ cursor: null, size: 100 })).rejects.toThrow(
      '거래내역 응답 형식이 올바르지 않습니다.',
    )
  })

  it('throws when the response body is null', async () => {
    mockedGet.mockResolvedValueOnce({ data: null })

    await expect(fetchTransactionHistory({ cursor: null, size: 100 })).rejects.toThrow(
      '거래내역 응답 형식이 올바르지 않습니다.',
    )
  })
})
