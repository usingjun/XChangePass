# 거래내역 테이블 렌더링(비가상화) 벤치마크

## 측정 환경

- production 빌드(`npm run build:benchmark && npm run preview:benchmark`)
- Chrome, 시크릿 창, 확장 프로그램 없음
- CPU 스로틀링 없음
- 데이터: `generateTransactionFixtures`(seed 기반 결정적 생성), 실제 API 미호출
- 화면: `/dev/transaction-benchmark`, `TransactionTable`의 `virtualized=false` 경로(naive 렌더링)

## 재현 방법

```bash
cd xcp-front-react
npm run build:benchmark && npm run preview:benchmark
# http://localhost:4173/dev/transaction-benchmark 에서 데이터 볼륨 선택 후
# Chrome DevTools Performance 탭으로 기록
```

## 주요 결과

| 데이터 | DOM 행 | Scripting | Rendering | 합계 | Frames | Heap |
|---|---|---|---|---|---|---|
| 1,000 | 1,000 | 194ms | 202ms | 586ms | 400ms | 26.3MB |
| 10,000 | 10,000 | 895ms | 1,180ms | 2,309ms | 3,026ms | 213MB |
| 30,000 | 30,000 | 4,067ms | 2,685ms | 7,160ms | 7,867ms | 652MB |
| 100,000 | 100,000 | 34,949ms | 12,158ms | 48,295ms | 48,180ms | 2,078MB → 탭 크래시 |

## 관찰 사항

- 100,000건에서 탭 크래시 발생(Chrome 오류 코드 5), 크래시 직전 Heap 2,078MB.
- Heap 구성에서 `LayoutTableCell` / `PhysicalBoxFragment` / `LayoutResult`가 상위 점유. 30,000건 기준 `LayoutTableCell` ×120,004개이며, 상위 3개 항목이 Heap의 절반 이상을 차지.
- DevTools Insights가 10,000건부터 "Optimize DOM size", "Forced reflow" 경고를 표시.
- 10,000 → 30,000 구간에서 Scripting 시간이 행 수 증가율(3배)보다 가파르게 증가(4.5배).

## table-layout: auto vs fixed 비교

`TransactionTable`의 `fixedLayout` prop(벤치마크 페이지의 "fixed layout" 체크박스)으로 같은 데이터·같은 컬럼 수에서 `table-layout: auto`(기존 경로)와 `table-layout: fixed`(컬럼별 `size` 명시, `columns.tsx`) 렌더 비용을 비교 측정했다. 각 셀 값은 위 "주요 결과" 표와 별도의 단발 측정 실행값이며 보정하지 않고 그대로 기록한다.

### 30,000건

| | auto | fixed |
|---|---|---|
| Scripting | 4,826ms | 2,975ms |
| Rendering | 3,434ms | 2,976ms |
| Frames | 9,421ms | 6,924ms |

### 10,000건

| | auto | fixed |
|---|---|---|
| Scripting | 1,098ms | 989ms |
| Rendering | 240ms | 1,349ms |
| Frames | 2,137ms | 3,300ms |

### 결론

- 30,000건에서 fixed layout 적용 시 Scripting 38% 감소, Frames 27% 감소.
- 10,000건에서는 Scripting 10% 감소로 효과가 작음 → 데이터가 많을수록 auto layout의 컬럼 폭 재계산 비용이 커진다는 가설과 일치.
- 다만 fixed 적용 후에도 30,000건 기준 약 7초 정지가 남음 → CSS만으로는 부족하고 DOM 노드 수 자체를 줄여야 한다.
- 10,000건 Frames가 fixed에서 더 높게 나온 것은 단일 측정이라 노이즈 가능성이 있음.
