# xcp-front-react

XChangePass 백엔드(`/v2/transaction`)와 붙는 거래내역(지갑 충전/출금/이체, 카드 결제, 환전) 통합 조회 화면. React 19 + TanStack Table v9로 만들었고, 무한 스크롤로 데이터가 계속 누적되는 화면 구조에서 렌더링 비용을 데이터 크기로부터 분리하기 위해 `@tanstack/react-virtual` 기반 행 가상화를 적용했다. 이 문서는 그 과정에서 실제로 겪은 성능 문제, 그걸 검증한 방법, 그리고 내린 설계 판단을 정리한다.

## 기술 스택

- **React 19**, **TypeScript 5.9**, **Vite 8**
- **TanStack Table v9** (`@tanstack/react-table`) — 컬럼 정의/row model
- **TanStack Virtual v3** (`@tanstack/react-virtual`) — 행 가상화
- **TanStack Query v5** (`@tanstack/react-query`) — 커서 기반 무한 스크롤(`useInfiniteQuery`)
- axios, react-router-dom v7
- Vitest + Testing Library(단위 테스트), ESLint(flat config) + Prettier

## 문제 → 분석 → 해결 → 결과

### 문제: naive 렌더링은 데이터가 늘수록 초선형으로 느려진다

무한 스크롤로 페이지가 계속 쌓이는 화면에서, 전체 행을 한 번에 DOM에 그대로 마운트하는 naive 렌더링을 그대로 뒀을 때 데이터 규모별로 실측한 결과다(`docs/benchmarks/frontend-transaction-table-rendering-2026-08-18.md`, production 빌드·시크릿 창·확장 프로그램 없음·CPU 스로틀링 없음 조건, seed 기반 결정적 fixture 데이터 사용).

| 데이터 | DOM 행 | Scripting | Rendering | 합계 | Frames | Heap |
|---|---|---|---|---|---|---|
| 1,000 | 1,000 | 194ms | 202ms | 586ms | 400ms | 26.3MB |
| 10,000 | 10,000 | 895ms | 1,180ms | 2,309ms | 3,026ms | 213MB |
| 30,000 | 30,000 | 4,067ms | 2,685ms | 7,160ms | 7,867ms | 652MB |
| 100,000 | 100,000 | 34,949ms | 12,158ms | 48,295ms | 48,180ms | 2,078MB → 탭 크래시 |

100,000건에서는 탭이 실제로 크래시했다(Chrome 오류 코드 5). 10,000 → 30,000 구간만 보면 행 수는 3배 늘었는데 Scripting 시간은 4.5배 늘어 — 데이터 크기에 비례하지 않고 초선형으로 증가했다.

### 분석: table-layout: auto가 원인이라는 가설

DevTools Insights가 10,000건부터 "Optimize DOM size", "Forced reflow" 경고를 표시했고, Heap 스냅샷 상위 항목이 `LayoutTableCell`/`PhysicalBoxFragment`/`LayoutResult` 같은 브라우저 레이아웃 객체였다(30,000건 기준 `LayoutTableCell` ×120,004개). 이를 근거로 `table-layout: auto`(컬럼 폭을 매번 콘텐츠 기준으로 재계산)가 초선형 증가의 한 원인이라는 가설을 세웠다.

### 검증: table-layout auto vs fixed 실측 비교

같은 데이터로 `table-layout: auto`(기존)와 `table-layout: fixed`(컬럼별 고정 폭)를 토글해 비교 측정했다.

**30,000건**

| | auto | fixed |
|---|---|---|
| Scripting | 4,826ms | 2,975ms |
| Rendering | 3,434ms | 2,976ms |
| Frames | 9,421ms | 6,924ms |

**10,000건**

| | auto | fixed |
|---|---|---|
| Scripting | 1,098ms | 989ms |
| Rendering | 240ms | 1,349ms |
| Frames | 2,137ms | 3,300ms |

30,000건에서는 fixed 적용 시 Scripting이 38% 줄고 Frames가 27% 줄었지만, 10,000건에서는 Scripting 개선이 10%에 그쳤다 — 데이터가 많을수록 auto layout의 컬럼 폭 재계산 비용이 커진다는 가설과 일치하는 결과였다. 다만 fixed 적용 후에도 30,000건 기준 약 7초 정지가 남아, **CSS 레이아웃 방식만으로는 부족하고 DOM 노드 수 자체를 줄여야 한다**는 결론으로 이어졌다.

### 해결: 행 가상화(`@tanstack/react-virtual`)

뷰포트 근처 행만 DOM에 마운트하도록 `TransactionTable`에 가상화 경로를 구현했다.

### 결과: 가상화 전/후 비교

**30,000건 (가상화 ON)**

| 항목 | 전 | 후 |
|---|---|---|
| DOM 행 | 30,000 | 25 |
| Scripting | 4,067ms | 196ms |
| Rendering | 2,685ms | 4ms |
| Painting | 169ms | 6ms |
| 합계 | 7,160ms | 247ms |
| Heap | 652MB | 36.0MB |

**100,000건 (가상화 ON)**

| 항목 | 전 | 후 |
|---|---|---|
| DOM 행 | 100,000 | 25 |
| Scripting | 34,949ms | 87ms |
| Rendering | 12,158ms | 2ms |
| Painting | 482ms | 3ms |
| System | 706ms | 55ms |
| 합계 | 48,295ms | 147ms |
| Heap | 2,078MB (탭 크래시) | 58.6MB |

1,000건은 가상화 OFF 시 DOM 행 1,000, ON 시 25였다.

데이터 양이 1,000건에서 100,000건까지 100배 늘어나는 동안 실제 DOM 행 수는 25로 고정됐다. Heap 스냅샷의 상위 항목도 `LayoutTableCell` 같은 브라우저 레이아웃 객체에서 `Object`/`array`/`string` 같은 JS 데이터 객체로 바뀌었다 — 가상화 이후 남은 메모리는 DOM이 아니라 데이터 배열 자체라는 뜻이다. (100,000건 측정치가 30,000건보다 빠르게 나온 건 단발 측정 노이즈로 보이지만, 두 경우 모두 DOM 행이 25로 동일하다는 사실은 노이즈와 무관하게 성립한다.)

## 설계상 판단한 것들

### 1. 실서비스 경로와 벤치마크 경로를 분리했다

실제 화면(`TransactionHistoryPage`)은 `/v2/transaction`을 커서 기반(서버가 준 `nextCursor`를 다음 요청에 그대로 echo, 페이지당 100건)으로 호출하는 `useInfiniteQuery` 무한 스크롤이다. 서버 페이지 크기 상한 때문에 실제 API로는 1,000~100,000건 규모의 렌더링 부하를 재현할 수 없어서, seed 기반으로 결정적 데이터를 생성하는 별도 벤치마크 화면(`/dev/transaction-benchmark`)을 뒀다. `TransactionTable`과 컬럼 정의(`columns.tsx`)는 두 화면이 그대로 공유하므로, 벤치마크에서 검증한 동작이 곧 실제 화면의 동작이다. 벤치마크 라우트는 `import.meta.env.DEV` 여부로 분기해 일반 `npm run build`에는 포함되지 않고, 측정이 필요할 때만 `npm run build:benchmark`로 별도 빌드한다.

### 2. 절대 위치 대신 스페이서 `<tr>` 방식을 택했다

행 가상화는 보통 각 행을 `position: absolute`로 배치하는 방식을 쓰지만, 이 프로젝트는 `<tbody>` 앞뒤에 높이만 채우는 빈 `<tr>` 두 개(스페이서)를 두고 실제 보이는 행 구간만 정상 문서 흐름으로 렌더링하는 방식을 택했다. 네이티브 `<table>` 구조가 그대로 남기 때문에, 앞서 검증한 `table-layout: auto`/`fixed` 토글과 `<colgroup>` 컬럼 폭 지정이 가상화 여부와 상관없이 그대로 조합된다 — flex/grid로 테이블을 재구성했다면 `table-layout` 자체가 무의미해져 이 조합이 깨졌을 것이다.

### 3. 무한 스크롤 sentinel과 가상 스크롤 컨테이너의 충돌을 피했다

무한 스크롤(다음 페이지 로드 트리거)은 `IntersectionObserver`로 감시하는 sentinel 엘리먼트를 쓰고, 가상화는 `useVirtualizer`가 스크롤 위치를 감시한다. 이 둘이 같은 스크롤 컨테이너 안에서 서로 어긋나지 않게 두 가지를 지켰다.

- `useVirtualizer`의 `getScrollElement`와 `IntersectionObserver`의 `root`가 정확히 같은 DOM 노드(스크롤 컨테이너)를 가리키게 했다 — 스크롤 요소가 갈리면 관측 대상과 실제 스크롤이 어긋난다.
- sentinel을 가상화 대상으로 취급하지 않고, 스크롤 컨테이너의 항상 존재하는 실제 자식으로 뒀다. 스페이서 행이 테이블의 실제 높이를 전체 데이터 크기만큼 유지해주므로, sentinel은 가상화로 몇 개의 행이 실제 마운트돼 있는지와 무관하게 "목록의 진짜 끝"에 그대로 위치한다.

## 실행 방법

Node 24 LTS(`.nvmrc`) 기준.

```bash
npm install
npm run dev              # http://localhost:5174 (실제 데이터가 필요한 /transactions는 백엔드(:8080)가 떠 있어야 함)
```

벤치마크 재현:

```bash
npm run build:benchmark && npm run preview:benchmark
# http://localhost:4173/dev/transaction-benchmark 에서 데이터 볼륨/virtualized/fixed layout 토글 선택 후
# Chrome DevTools Performance 탭으로 기록
```

검증:

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```
