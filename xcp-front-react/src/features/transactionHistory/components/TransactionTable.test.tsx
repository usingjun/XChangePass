import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import TransactionTable from './TransactionTable'
import { generateTransactionFixtures } from '../fixtures/generateTransactionFixtures'

describe('TransactionTable', () => {
  it('renders rows for each transaction, using data.transactionType as discriminant', () => {
    const data = generateTransactionFixtures(5, 42)

    render(<TransactionTable data={data} virtualized={false} />)

    expect(screen.getByRole('columnheader', { name: '거래일시' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '유형' })).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(data.length + 1) // +1 for header row
  })

  it('shows an empty message instead of an empty table when there is no data', () => {
    render(<TransactionTable data={[]} virtualized={false} />)

    expect(screen.getByText('표시할 거래내역이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('renders every row when virtualized but the data fully fits within the viewport + overscan', () => {
    const data = generateTransactionFixtures(3, 7)

    render(<TransactionTable data={data} virtualized />)

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(data.length + 1) // +1 for header row
  })

  it('renders far fewer DOM rows than the data volume when virtualized with a large dataset', () => {
    const data = generateTransactionFixtures(5_000, 11)

    render(<TransactionTable data={data} virtualized />)

    const rowCount = screen.getAllByRole('row').length
    expect(rowCount).toBeGreaterThan(1) // header + at least some data rows
    expect(rowCount).toBeLessThan(data.length + 1) // windowed, not the full dataset
  })
})
