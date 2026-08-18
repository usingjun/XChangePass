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

  it('shows the not-yet-implemented placeholder when virtualized is toggled on', async () => {
    const user = userEvent.setup()
    render(<TransactionBenchmarkPage />)

    await user.click(screen.getByRole('checkbox', { name: '가상화' }))

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByText(/가상화 렌더링은 다음 단계에서 구현 예정입니다/)).toBeInTheDocument()
  })
})
