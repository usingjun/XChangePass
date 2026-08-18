import { useEffect, useRef } from 'react'
import { useTable } from '@tanstack/react-table'
import type { TransactionResponse } from '../types'
import { features, transactionColumns } from './columns'

interface TransactionTableProps {
  data: TransactionResponse[]
  // 이번 단계에서는 false 경로만 구현한다. true는 다음 단계(6.3 가상화)에서 채운다.
  virtualized: boolean
  // 벤치마크 전용: table-layout: auto(기본, 미지정 시)와 fixed를 비교 측정하기 위한 토글.
  // TransactionHistoryPage는 이 prop을 넘기지 않으므로 실제 화면 동작은 그대로다.
  fixedLayout?: boolean
  // 스크롤이 하단 sentinel에 도달했을 때 호출된다. 다음 페이지를 더 불러올지는
  // 이 콜백을 넘기는 쪽(서버 상태를 쥔 페이지)의 몫이지 이 컴포넌트의 몫이 아니다.
  onReachEnd?: () => void
}

const SCROLL_CONTAINER_HEIGHT = 640

function TransactionTable({ data, virtualized, fixedLayout = false, onReachEnd }: TransactionTableProps) {
  const table = useTable({
    features,
    columns: transactionColumns,
    data,
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
      <table className={`transaction-table${fixedLayout ? ' transaction-table--fixed' : ''}`}>
        {fixedLayout && (
          <colgroup>
            {table.getHeaderGroups()[0]?.headers.map((header) => (
              <col key={header.id} style={{ width: header.getSize() }} />
            ))}
          </colgroup>
        )}
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th key={header.id}>
                  {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id}>
              {row.getAllCells().map((cell) => (
                <td key={cell.id}>
                  <table.FlexRender cell={cell} />
                </td>
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
