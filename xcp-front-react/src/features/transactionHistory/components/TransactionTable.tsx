import { useEffect, useRef } from 'react'
import { useTable } from '@tanstack/react-table'
import { useVirtualizer } from '@tanstack/react-virtual'
import type { TransactionResponse } from '../types'
import { features, transactionColumns } from './columns'

interface TransactionTableProps {
  data: TransactionResponse[]
  // true면 @tanstack/react-virtual로 뷰포트 주변 행만 DOM에 마운트한다(6.3 가상화).
  virtualized: boolean
  // 벤치마크 전용: table-layout: auto(기본, 미지정 시)와 fixed를 비교 측정하기 위한 토글.
  // TransactionHistoryPage는 이 prop을 넘기지 않으므로 실제 화면 동작은 그대로다.
  fixedLayout?: boolean
  // 스크롤이 하단 sentinel에 도달했을 때 호출된다. 다음 페이지를 더 불러올지는
  // 이 콜백을 넘기는 쪽(서버 상태를 쥔 페이지)의 몫이지 이 컴포넌트의 몫이 아니다.
  onReachEnd?: () => void
}

const SCROLL_CONTAINER_HEIGHT = 640

// .transaction-table tbody td의 padding(8px*2) + 14px 폰트 한 줄 높이(~17px) + border-bottom(1px)
// 기준 초기 추정치일 뿐이다. 실제 렌더된 행 높이는 rowVirtualizer.measureElement가 각 행을 측정해
// 보정한다 — 이 테이블은 auto 모드는 white-space: nowrap, fixed 모드는 ellipsis라 항상 한 줄이므로
// 측정값이 이 추정치 근처로 수렴하고, 고정 높이로 가정하지 않는 이유는 시스템 폰트 메트릭이
// OS/브라우저마다 미세하게 달라 추정치만으로는 행 간 겹침/틈이 생길 수 있기 때문이다. 측정 비용은
// 화면에 마운트된 행(오버스캔 포함) 수에만 비례해 전체 데이터 크기와 무관하게 상수로 유지된다.
const ESTIMATED_ROW_HEIGHT = 36

// 뷰포트에 보이는 행(640px / ~36px ≈ 18행) 위아래로 두는 여유분. 무한스크롤 sentinel의
// rootMargin(200px, 약 5~6행)과 자릿수를 맞춰, 관성/빠른 스크롤 중에도 다음 프레임이
// virtual item을 재계산하기 전에 흰 화면이 보이지 않을 정도의 완충만 두고, 데이터 총량
// (최대 100,000행)과는 무관하게 고정한다.
const OVERSCAN_ROW_COUNT = 6

function TransactionTable({ data, virtualized, fixedLayout = false, onReachEnd }: TransactionTableProps) {
  const table = useTable({
    features,
    columns: transactionColumns,
    data,
  })

  const rows = table.getRowModel().rows

  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // virtualized가 false여도 훅은 항상 호출한다(Rules of Hooks) — 체크박스로 즉시 전환 가능해야
  // 하므로 마운트 시점에 조건부로 훅을 부르는 대신, 아래 렌더링에서만 결과를 쓸지 말지 가른다.
  const rowVirtualizer = useVirtualizer<HTMLDivElement, HTMLTableRowElement>({
    count: rows.length,
    getScrollElement: () => scrollContainerRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: OVERSCAN_ROW_COUNT,
  })

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

  const columnCount = table.getHeaderGroups()[0]?.headers.length ?? 0
  const virtualRows = virtualized ? rowVirtualizer.getVirtualItems() : []
  const totalSize = virtualized ? rowVirtualizer.getTotalSize() : 0
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0
  const paddingBottom = virtualRows.length > 0 ? totalSize - virtualRows[virtualRows.length - 1].end : 0

  return (
    // 무한스크롤 sentinel(IntersectionObserver의 root)과 가상 스크롤이 관측하는 스크롤 컨테이너
    // (rowVirtualizer의 getScrollElement)는 반드시 이 div 하나를 공유해야 한다 — 스크롤 요소가
    // 둘로 갈리면(예: 가상화 쪽에 별도 wrapper를 두면) observer가 실제 스크롤과 다른 요소를 보게 돼
    // 교차 판정이 어긋난다.
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
          {virtualized ? (
            <>
              {/* 실제 렌더된 행들 앞뒤의 빈 공간을 스페이서 행으로 채워 스크롤 컨테이너의
                  scrollHeight를 데이터 전체 크기(totalSize) 그대로 유지한다. 네이티브 <table>
                  구조를 그대로 쓰므로(absolute-positioned row 대신) table-layout: auto/fixed
                  토글과 그대로 조합된다. */}
              {paddingTop > 0 && (
                <tr aria-hidden="true">
                  <td colSpan={columnCount} style={{ height: paddingTop, padding: 0, border: 0 }} />
                </tr>
              )}
              {virtualRows.map((virtualRow) => {
                const row = rows[virtualRow.index]
                return (
                  <tr key={row.id} data-index={virtualRow.index} ref={rowVirtualizer.measureElement}>
                    {row.getAllCells().map((cell) => (
                      <td key={cell.id}>
                        <table.FlexRender cell={cell} />
                      </td>
                    ))}
                  </tr>
                )
              })}
              {paddingBottom > 0 && (
                <tr aria-hidden="true">
                  <td colSpan={columnCount} style={{ height: paddingBottom, padding: 0, border: 0 }} />
                </tr>
              )}
            </>
          ) : (
            rows.map((row) => (
              <tr key={row.id}>
                {row.getAllCells().map((cell) => (
                  <td key={cell.id}>
                    <table.FlexRender cell={cell} />
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
      {/* sentinel은 가상화 대상이 아니라 스크롤 컨테이너의 항상 존재하는 실제 자식이다 — 스페이서
          행이 totalSize만큼 테이블의 실제 높이를 유지해주므로, sentinel은 가상화 여부와 무관하게
          "데이터 전체 목록의 진짜 끝"에 그대로 위치한다. 만약 sentinel을 가상 아이템 취급해 뷰포트에
          없을 때 DOM에서 걷어냈다면, 스크롤이 끝에 닿아도 관측 대상 자체가 없어 다음 페이지 로드가
          트리거되지 않았을 것이다. */}
      <div ref={sentinelRef} className="transaction-table__sentinel" aria-hidden="true" />
    </div>
  )
}

export default TransactionTable
