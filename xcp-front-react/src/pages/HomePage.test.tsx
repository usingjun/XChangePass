import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HomePage from './HomePage'

describe('HomePage', () => {
  it('renders the app heading', () => {
    render(<HomePage />, { wrapper: MemoryRouter })
    expect(screen.getByRole('heading', { name: 'xcp-front-react' })).toBeInTheDocument()
  })

  it('links to the transaction history screen', () => {
    render(<HomePage />, { wrapper: MemoryRouter })
    expect(screen.getByRole('link', { name: '거래내역' })).toHaveAttribute('href', '/transactions')
  })

  it('links to the dev-only benchmark screen in dev mode', () => {
    render(<HomePage />, { wrapper: MemoryRouter })
    expect(screen.getByRole('link', { name: /벤치마크/ })).toHaveAttribute(
      'href',
      '/dev/transaction-benchmark',
    )
  })
})
