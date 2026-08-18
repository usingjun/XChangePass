---
name: transaction-column
description: Use when adding, removing, or resizing a column in the transaction history table (xcp-front-react/src/features/transactionHistory/components/columns.tsx) — covers TanStack Table v9 column definition rules, why size + columnSizingFeature registration is required, how it interacts with the fixed-layout benchmark toggle, and the formatter/cell-renderer split this file uses.
---

# 거래내역 테이블 컬럼 추가

`xcp-front-react/src/features/transactionHistory/components/columns.tsx`에 새 컬럼을 추가하거나 기존 컬럼을 고칠 때 따르는 규칙. 이 컬럼 정의는 실제 화면(`TransactionHistoryPage`)과 벤치마크 화면(`/dev/transaction-benchmark`)이 그대로 공유하므로, 여기서 한 번 고치면 양쪽 다 영향을 받는다.

## 1. Column Definition 작성 규칙

- 필드가 응답 DTO(`TransactionResponse`)에 그대로 있으면 `columnHelper.accessor('필드명', {...})`을 쓴다 (예: `transactionTime` 컬럼).
- 여러 값을 조합해야 나오는 파생값이면 `columnHelper.accessor((row) => 파생함수(row), { id: '...', ... })` 형태를 쓴다 (예: `detail`, `amount` 컬럼이 각각 `renderDetail`/`renderAmount`를 호출).
- `id`와 `header`는 항상 명시한다 — accessor fn 형태는 `id`를 자동으로 추론 못 하므로 생략하면 타입 에러가 난다.
- 컬럼 순서/가시성 feature(`columnOrderingFeature`, `columnVisibilityFeature`)는 등록돼 있지 않다(`export const features = tableFeatures({ columnSizingFeature })`) — 컬럼 순서를 바꾸려면 `transactionColumns` 배열 안 순서 자체를 바꾼다.

## 2. size 지정 + columnSizingFeature 등록 (v9 특유)

TanStack Table v9에서 `size`/`header.getSize()`는 opt-in feature다. `columnSizingFeature`를 `tableFeatures({...})`에 등록해야만 `columnDef.size`가 타입에 잡히고 `header.getSize()`가 존재한다 — 이 프로젝트는 이미 등록돼 있다. 새 컬럼을 추가할 때:

- **반드시 `size: <number>`를 지정한다.** 생략하면 v9 기본값(150px, 내부 `getDefaultColumnSizingColumnDef()`)이 그대로 적용돼 기존 4개 컬럼(거래일시 180 / 유형 90 / 내용 280 / 금액 220)과 시각적 균형이 깨진다.
- 값은 콘텐츠 폭 기준으로 잡는다 — 날짜/뱃지처럼 폭이 고정인 컬럼은 좁게(90~180), 자유 텍스트/금액처럼 길이가 변하는 컬럼은 넓게(220~280) 잡는 게 기존 컬럼들의 패턴이다.

## 3. fixed layout 모드의 colgroup에도 자동 반영된다 — 하지만 size를 빠뜨리면 거기서만 티가 난다

`TransactionTable.tsx`의 `<colgroup>`(`fixedLayout=true`일 때만 렌더)은 `table.getHeaderGroups()[0]?.headers`를 순회해 자동으로 만들어진다 — **컬럼을 추가/삭제해도 `TransactionTable.tsx`를 직접 고칠 필요는 없다.** 다만:

- 2번에서 `size`를 빠뜨리면 `<col style={{ width: header.getSize() }} />`가 기본값 150px을 그대로 반영해버려서, `table-layout: fixed`를 켰을 때만 폭이 어긋나는 버그로 나타난다. `table-layout: auto`(기본) 모드에서는 브라우저가 콘텐츠 기준으로 다시 계산하기 때문에 안 보이므로, 새 컬럼을 넣은 뒤에는 벤치마크 페이지의 "fixed layout" 체크박스를 반드시 켜서 확인한다.
- 가상화(`virtualized=true`)의 스페이서 행 `colSpan`도 `table.getHeaderGroups()[0]?.headers.length`(`TransactionTable.tsx`의 `columnCount`)로 동적 계산되므로 여기도 별도로 고칠 곳은 없다. 그래도 컬럼을 추가한 뒤에는 auto/fixed × virtualized on/off 네 조합을 한 번씩 눈으로 확인한다 — 넷 다 같은 컬럼 정의를 공유하지만 실제 레이아웃 계산 경로(네이티브 table-layout vs 가상 스크롤 스페이서)는 서로 다르다.

## 4. Cell Renderer와 Formatter의 분리

이 파일은 "값을 문자열로 바꾸는 순수 함수"(포맷터)와 "그 값을 JSX로 그리는 함수"(셀 렌더러)를 분리해서 유지한다:

- **포맷터**: `formatDateTime`, `formatAmount`, `formatCurrencyAmount`, `renderDetail`, `renderAmount` — 입력을 받아 문자열을 리턴하는 순수 함수. `cell` 콜백 안이 아니라 파일 상단에 독립적으로 정의한다.
- **셀 렌더러**: `cell: (info) => ...` — 포맷터를 호출하거나(`transactionTime` 컬럼처럼 `cell: (info) => formatDateTime(info.getValue())`), JSX가 꼭 필요할 때만(`transactionType` 컬럼의 뱃지 `<span>`처럼) 그 안에서 직접 렌더링한다.

새 컬럼을 추가할 때 서식 로직(날짜 포맷, 금액 콤마, 조건별 텍스트 조합)을 `cell` 콜백 안에 인라인으로 쓰지 않는다 — 별도 함수로 뽑아야 재사용/단위 테스트가 되고 기존 포맷터들과 스타일이 일관된다. `amount` 계열 필드는 BigDecimal 직렬화 문자열이라 새 포맷터도 `Number()`로 변환하지 않고 문자열 그대로 다룬다(정밀도 손실 방지 — `formatAmount`가 이미 이 원칙으로 짜여 있다).

## 5. discriminant는 `data.transactionType`, `@class`는 절대 쓰지 않는다

거래 종류별로 분기해야 하는 컬럼(`renderDetail`, `renderAmount`, `transactionType` 뱃지)은 전부 `row.data.transactionType`(`'WALLET' | 'CARD' | 'EXCHANGE'`)으로 스위치한다. 백엔드가 Jackson `@JsonTypeInfo(use=Id.CLASS)`로 내려주는 `"@class"`(Java 클래스 풀네임 문자열)는 **절대 파싱하거나 조건문에 쓰지 않는다** — `xcp-front-react/CLAUDE.md`에 이미 명시된 규칙이고, 백엔드 구현 클래스명이 바뀌면 조용히 깨지는 함정이다. 새 서브타입 조건(카드의 `cardType`, 지갑의 `walletType`/`direction` 같은)이 필요하면 먼저 `data.transactionType`으로 분기한 다음 그 안에서 서브타입 필드를 본다 — 기존 `renderDetail`/`renderAmount`의 `switch (data.transactionType)` 패턴을 그대로 따른다.
