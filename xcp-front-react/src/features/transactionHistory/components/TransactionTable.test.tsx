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

  it('does not render the naive table when virtualized (true path is not implemented yet)', () => {
    const data = generateTransactionFixtures(3, 7)

    render(<TransactionTable data={data} virtualized />)

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
