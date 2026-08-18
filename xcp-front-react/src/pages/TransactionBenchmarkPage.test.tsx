import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TransactionBenchmarkPage from './TransactionBenchmarkPage'

describe('TransactionBenchmarkPage', () => {
  it('renders the default 1,000-row volume without virtualization', () => {
    render(<TransactionBenchmarkPage />)

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(1_000 + 1)
  })

  it('regenerates fixtures when the volume selection changes', async () => {
    const user = userEvent.setup()
    render(<TransactionBenchmarkPage />)

    await user.selectOptions(screen.getByRole('combobox'), '10000')

    expect(screen.getAllByRole('row')).toHaveLength(10_000 + 1)
  })

  it('renders far fewer DOM rows than the data volume once virtualization is toggled on', async () => {
    const user = userEvent.setup()
    render(<TransactionBenchmarkPage />)

    await user.click(screen.getByRole('checkbox', { name: '가상화' }))

    expect(screen.getByRole('table')).toBeInTheDocument()
    const rowCount = screen.getAllByRole('row').length
    expect(rowCount).toBeGreaterThan(1) // header + at least some data rows
    expect(rowCount).toBeLessThan(1_000 + 1) // windowed, not the full default volume
  })
})
