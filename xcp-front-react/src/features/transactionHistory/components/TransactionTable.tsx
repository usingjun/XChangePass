import { useEffect, useRef } from 'react'
import { flexRender } from '@tanstack/react-table'
import { useLegacyTable } from '@tanstack/react-table/legacy'
import type { TransactionResponse } from '../types'
import { transactionColumns } from './columns'

interface TransactionTableProps {
  data: TransactionResponse[]
  // 이번 단계에서는 false 경로만 구현한다. true는 다음 단계(6.3 가상화)에서 채운다.
  virtualized: boolean
  // 스크롤이 하단 sentinel에 도달했을 때 호출된다. 다음 페이지를 더 불러올지는
  // 이 콜백을 넘기는 쪽(서버 상태를 쥔 페이지)의 몫이지 이 컴포넌트의 몫이 아니다.
  onReachEnd?: () => void
}

const SCROLL_CONTAINER_HEIGHT = 640

function TransactionTable({ data, virtualized, onReachEnd }: TransactionTableProps) {
  const table = useLegacyTable({
    data,
    columns: transactionColumns,
  })

  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!onReachEnd) return
    const sentinel = sentinelRef.current
    const root = scrollContainerRef.current
    if (!sentinel || !root) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          onReachEnd()
        }
      },
      { root, rootMargin: '200px' },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [onReachEnd])

  if (data.length === 0) {
    return <p className="transaction-table__empty">표시할 거래내역이 없습니다.</p>
  }

  if (virtualized) {
    return (
      <p className="transaction-table__placeholder">
        가상화 렌더링은 다음 단계에서 구현 예정입니다. (현재 {data.length.toLocaleString('ko-KR')}건)
      </p>
    )
  }

  return (
    <div className="transaction-table__scroll" ref={scrollContainerRef} style={{ height: SCROLL_CONTAINER_HEIGHT }}>
      <table className="transaction-table">
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th key={header.id}>
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id}>
              {row.getVisibleCells().map((cell) => (
                <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div ref={sentinelRef} className="transaction-table__sentinel" aria-hidden="true" />
    </div>
  )
}

export default TransactionTable
