# 거래내역 통합조회 React 화면 — 조사 및 구현 계획

## 0. 배경 및 경위

원래 요청은 "`xcp-front/`에 React 거래내역 통합조회 화면을 추가"였다. 그러나 `xcp-front/`를 조사한 결과 이 프로젝트는 **Vue 3(Vite + Vue Router + Pinia)** 기반이며 React 관련 코드/의존성이 전혀 없다는 사실이 확인됐다.

이 불일치를 사용자에게 확인한 결과, 기존 Vue 프로젝트에 화면을 맞춰 넣는 대신 **`xcp-front/`와 완전히 별개인 새 React 전용 프로젝트(`xcp-front-react/`)를 저장소에 추가**하고, 그 프로젝트 안에 거래내역 통합조회 화면을 만드는 방향으로 결정했다. 이 문서는 그 결정을 위한 조사 결과와 구현 계획을 담는다.

**이 문서는 계획 문서이며, 어떤 코드/의존성도 아직 설치·생성되지 않았다.**

---

## 1. xcp-front/ (기존 Vue 프로젝트) 조사 요약

| 항목 | 내용 |
|---|---|
| 프레임워크 | **Vue 3.5.13** (React 아님) + Vite 6.2.4 + vue-router 4.5.0 + Pinia 3.0.2 |
| 언어 | JavaScript, TypeScript 아님 (`jsconfig.json`, `@/*` alias만 존재) |
| 패키지 매니저 | npm, `xcp-front/package-lock.json` 독립 존재 |
| 스크립트 | `dev` / `build` / `preview`만 존재. `lint`/`test` 없음 |
| 테스트/E2E | 없음 (Vitest/Jest/Cypress/Playwright 전무) |
| 서버 상태 라이브러리 | 없음 — axios + `onMounted()` + 로컬 `ref()` 패턴 |
| API 클라이언트 | `src/api/index.js`의 `authClient`/`publicClient` (axios, `baseURL: 'http://localhost:8080'` 하드코딩, `withCredentials: true`, 401 시 `/token-refresh` 후 재시도). `src/api/axios.js`에 중복/불일치하는 두 번째 axios 세팅이 공존. `.env` 파일 전무 |
| 인증 방식 | httpOnly 쿠키(`accessToken`) 기반. `Authorization` 헤더 아님. `localStorage`는 `isLoggedIn` UI 플래그만 저장 |
| CSS/UI | Bootstrap 5 + Tailwind 4 + DaisyUI 혼용 |
| Table/Grid | 재사용 가능한 공통 컴포넌트 없음. `<table>` 요소 자체가 코드베이스에 없고 전부 div 기반 리스트 |
| 가장 가까운 기존 패턴 | `src/views/CardTransactions.vue` + `src/api/transactionApi.js` — 카드 거래내역 전용, cursor 기반 "더 보기" 버튼 페이지네이션. 지갑+카드+환전 통합 화면은 아직 없음 |
| 성숙도 | 핵심 기능(로그인/지갑/환전/카드/카드거래내역)은 동작하지만 테스트·lint·env 설정이 없고 죽은 코드가 일부 존재하는 다듬어지지 않은 MVP |

**루트 `package.json`/`package-lock.json`과의 관계**: 저장소 루트에도 `package.json`/`package-lock.json`이 있으나 `bootstrap`, `pinia`만 담긴 이질적인 잔재 파일이며 `xcp-front/`와 npm workspace 관계가 아니다(루트에 `"workspaces"` 필드 없음, 루트 `node_modules`가 xcp-front가 필요로 하는 vite/axios/daisyui 등을 포함하지 않음). `k6-scripts/`와도 무관하다(해당 디렉터리는 `package.json` 자체가 없음). 따라서 xcp-front는 **완전히 독립적인 프로젝트**로 취급해야 한다.

---

## 2. 백엔드 — 거래내역 통합조회 API 조사 결과

### 2.1 엔드포인트

- `GET /v2/transaction` (`TransactionController`, `src/main/java/bumblebee/xchangepass/domain/transaction/controller/TransactionController.java`)
- 인증 필요 (Spring Security `anyRequest().authenticated()`; `/v2/transaction`은 permit-all 목록에 없음)

### 2.2 요청 파라미터

`TransactionSearchCondition` (`@ModelAttribute`) + `size`(`@RequestParam`, 기본 50, 범위 1~100, 벗어나면 `T013` 에러):

| 파라미터 | 타입 | 비고 |
|---|---|---|
| `transactionType` | `TransactionType` enum: `WALLET` / `CARD` / `EXCHANGE` | |
| `cardTransactionType` | `CardTransactionType` enum: `PAYMENT` / `DEPOSIT` / `REFUND` | |
| `merchantName` | `String` | non-blank면 카드 거래만 검색 |
| `minAmount` / `maxAmount` | `BigDecimal` | |
| `currency` | `String` | |
| `direction` | `TransactionDirection` enum: `ALL` / `SENT` / `RECEIVED` | 기본 `ALL`, 지갑 전용 개념 |
| `walletTransactionType` | `WalletTransactionType` enum: `DEPOSIT` / `WITHDRAWAL` / `TRANSFER` | |
| `startDate` / `endDate` | `LocalDateTime` (ISO datetime) | |
| `cursor` | `String` | 이전 응답의 `nextCursor`를 그대로 전달 |

### 2.3 응답 구조

```ts
// TransactionPageResponse
interface TransactionPageResponse {
  items: TransactionResponse[];
  nextCursor: string | null;
  hasNext: boolean;
}

// TransactionResponse
interface TransactionResponse {
  userId: number;
  beforeCurrency: string;   // ISO 통화 코드 (java.util.Currency 직렬화)
  afterCurrency: string;
  transactionTime: string;  // ISO datetime
  data: WalletTransactionDto | CardTransactionDto | ExchangeTransactionDto;
}

interface WalletTransactionDto {
  transactionType: 'WALLET';
  counterpartyUserId: number;
  amount: string;            // BigDecimal 직렬화, 정밀도 손실 방지를 위해 string 취급 권장 (2.6 참고)
  direction: 'ALL' | 'SENT' | 'RECEIVED';
  walletType: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
}

interface CardTransactionDto {
  transactionType: 'CARD';
  merchant: string;
  amount: string;
  balanceAfter: string;
  cardType: 'PAYMENT' | 'DEPOSIT' | 'REFUND';
}

interface ExchangeTransactionDto {
  transactionType: 'EXCHANGE';
  beforeAmount: string;
  afterAmount: string;
  rate: string;
}
```

**중요**: `data` 필드는 Jackson `@JsonTypeInfo(use = Id.CLASS, ...)`로 직렬화되어 실제 응답에는 `"@class"` 프로퍼티에 **정식 Java 클래스 풀네임**(예: `"bumblebee.xchangepass.domain.transaction.dto.response.WalletTransactionDto"`)이 들어간다. **이 값을 프론트에서 파싱/의존하지 않는다.** 대신 각 서브 DTO에 공통으로 존재하는 `data.transactionType`(`WALLET`/`CARD`/`EXCHANGE`)을 discriminant로 사용해 TypeScript discriminated union을 구성한다.

### 2.4 status 필드에 대한 결론

**wallet/card 거래에는 status 필드가 응답에 없다.** exchange 거래만 내부적으로 `ExchangeTransactionStatus.COMPLETED`로 하드 필터링되어 나오므로(`ExchangeTransactionRepositoryImpl`), PENDING/FAILED 환전 건은 애초에 이 API에 나타나지 않고, 세 서브 DTO 중 어디에도 상태 필드가 없다.

→ **없는 필드를 위해 백엔드를 수정하지 않는다.** 화면에서 "거래 상태" 컬럼은 설계하지 않고, 대신 **거래 유형(`transactionType`)과 서브타입(`walletTransactionType`/`cardTransactionType`/`direction`)**을 Custom Cell Renderer의 주요 대상으로 삼는다(4.3, 6절 참고).

### 2.5 페이지네이션 — keyset/cursor

- `TransactionCursor` (`domain/transaction/dto/cursor/TransactionCursor.java`): `{ version, transactionTime, transactionType, transactionId }`
- 정렬 기준 고정: `transactionTime DESC, transactionType ASC, transactionId DESC`
- `TransactionCursorCodec`가 이 값을 JSON 직렬화 후 **Base64 URL-safe, no-padding** 인코딩 — 완전히 opaque한 문자열
- 잘못된/오래된 버전 커서는 `T012`(`INVALID_TRANSACTION_CURSOR`, 400) 에러

→ **프론트는 커서를 절대 파싱하거나 직접 구성하지 않는다.** 이전 응답의 `nextCursor`를 다음 요청의 `cursor` 파라미터에 그대로 echo한다.

### 2.6 확인 필요 항목

- BigDecimal 금액 필드(`amount`, `balanceAfter`, `beforeAmount`, `afterAmount`, `rate`)가 JSON 직렬화 시 정밀도 손실 없이 오는지(Jackson 기본 동작) — 구현 착수 시 실제 응답을 찍어 확인. 확인 전까지는 TS 타입에서 `number`가 아닌 `string`으로 취급해 안전하게 파싱한다.

### 2.7 CORS / 인증 / 로컬 호출 방법

- CORS는 `SecurityConfig`(`global/security/SecurityConfig.java`)의 `CorsConfigurationSource` 빈에서 처리 (별도 `WebMvcConfigurer`/`@CrossOrigin` 없음).
  - 허용 origin: `cors.front.url`(로컬 기본값 `http://localhost:3000`), `cors.url`(`http://example.com`) — `application.yaml`.
  - `allowCredentials: true`, 허용 메서드 `GET/POST/PUT/DELETE/OPTIONS`.
- **Vite 기본 개발 포트(5173)는 현재 허용 origin 목록에 없다.** 새 React 프로젝트를 로컬에서 백엔드에 실제로 붙여 테스트하려면 둘 중 하나가 필요하다:
  1. 새 프로젝트의 dev 서버 포트를 `3000`으로 맞춘다 (단, xcp-front도 그 포트를 쓰고 있다면 동시 실행이 불가능할 수 있음 — 확인 필요).
  2. 백엔드 `SecurityConfig`의 허용 origin 목록에 새 포트를 추가한다 (작은 코드 변경, **이번 계획 범위 밖, 별도 승인 필요**).
  - 이 문서는 두 옵션만 기록하고 최종 선택 및 백엔드 수정은 실행하지 않는다.
- 인증은 **httpOnly 쿠키(`accessToken`) 기반** (`JwtAuthFilter.resolveTokenFromCookie`). `Authorization: Bearer` 헤더가 아니다. 프론트는 axios 요청에 항상 `withCredentials: true`를 설정해야 한다.
- 로컬 기본 base URL: **`http://localhost:8080`** (context path 없음, `server.port` 미설정으로 Spring Boot 기본값 8080 사용).

---

## 3. 신규 프로젝트 스택 결정

- **위치**: 저장소 루트의 `xcp-front-react/` (기존 `xcp-front/`와 완전히 별개, 워크스페이스 아님, 독립 `package.json`/lockfile).
- **빌드 도구**: Vite + React 18 + **TypeScript**.
- **라우팅**: `react-router-dom` v6.
- **서버 상태**: `@tanstack/react-query` v5 — `useInfiniteQuery`가 cursor 기반 페이지네이션(`nextCursor` → 다음 페이지 `pageParam`)과 자연스럽게 매칭된다.
- **API 클라이언트**: axios, `withCredentials: true`, baseURL은 **`VITE_API_BASE_URL` 환경변수**로 관리(`.env.development` 기본값 `http://localhost:8080`) — xcp-front가 4곳에 하드코딩했던 실수를 반복하지 않는다.
- **UI 상태**: React local state (+ 필터는 `useSearchParams`로 URL과 동기화하는 것을 검토).

---

## 4. Table 라이브러리 후보 비교

새 프로젝트는 (xcp-front가 JS인 것과 무관하게) **TypeScript**로 진행한다. 비교 기준은 "기능 개수"가 아니라 **(a) column definition을 직접 설계할 수 있는지 + 그 타입 설계 방식, (b) 가상화 라이브러리와의 결합 방식**이다.

### 4.1 `@tanstack/react-table` v8 — 추천

- Headless. `createColumnHelper<TransactionResponse>()`로 컬럼 정의 자체가 제네릭 타입을 가진다.
- `accessorFn`에 임의 로직(예: `data.transactionType`으로 분기해 wallet/card/exchange 서브타입 필드에 타입 안전하게 접근)을 넣을 수 있어, 위 2.3의 다형성 데이터 모델(discriminated union)에 그대로 대응 가능.
- 렌더 마크업 소유권이 전적으로 우리 쪽에 있어 Tailwind 등 자유로운 스타일링과 잘 맞는다.
- 가상화는 **같은 TanStack 계열의 `@tanstack/react-virtual`**을 행 렌더링 루프에 그대로 얹는 방식으로 결합 — 별도 어댑터/라이브러리 교체 없이 **같은 컴포넌트 트리 안에서 naive 렌더 ↔ 가상화 렌더를 prop 하나로 전환**할 수 있다. 이는 아래 5절 벤치마크 라우트 요건과 정확히 맞아떨어진다.

### 4.2 AG Grid (Community, `ag-grid-react`)

- `ColDef<TransactionResponse>[]` 형태로 공식 TS 타입은 지원되지만, `valueGetter`/`cellRenderer`가 그리드 엔진의 계약(row model, cell params)에 종속돼 있어 컬럼 정의가 "직접 설계"보다는 "그리드 API에 맞춰 채우는" 형태에 가깝다.
- 가상화는 자체 내장 row virtualization이 기본 동작이라 별도 결합이 필요 없는 대신, **naive(비가상화) 렌더와 가상화 렌더를 같은 빌드에서 나란히 토글 비교하기가 TanStack 조합보다 번거롭다**(그리드 자체를 끄고 켜는 옵션이 제한적).

### 4.3 결론

**TanStack Table(+ TanStack Virtual)을 1차 추천**한다. 타입 설계 자유도와, 이번 계획에서 요구되는 "naive/가상화 전환 가능한 벤치마크 라우트" 요건 모두에 더 적합하다. AG Grid는 "정렬/필터/가상화가 즉시 다 갖춰진 그리드가 급히 필요해지는" 상황이 오면 대안으로 남겨둔다.

---

## 5. 컴포넌트 구조 — 서버 상태 vs UI 상태 분리

- **서버 상태** (TanStack Query 캐시): 거래내역 목록, 커서, 로딩/에러 상태 → `features/transactionHistory/hooks/useTransactionHistoryQuery.ts`.
- **UI 상태** (로컬 React state, 필터는 `useSearchParams` 연동 검토): 필터 폼 값(`TransactionFilterBar`), 상세 모달 열림 여부.
- **`columns.tsx`** (Custom Cell Renderer 설계 대상): status 필드가 없으므로 **거래 유형(`data.transactionType`: WALLET/CARD/EXCHANGE)과 서브타입(`walletTransactionType`/`cardTransactionType`/`direction`)**을 기준으로 뱃지·아이콘·색상을 분기하는 셀 렌더러를 커스터마이징 단계의 주 대상으로 삼는다.

디렉터리 스케치:

```
xcp-front-react/src/
├── lib/apiClient.ts
├── router/
├── pages/
│   ├── TransactionHistoryPage.tsx
│   └── TransactionBenchmarkPage.tsx        (개발 전용, 5.2절)
└── features/transactionHistory/
    ├── types.ts
    ├── api/transactionHistoryApi.ts
    ├── hooks/useTransactionHistoryQuery.ts
    ├── fixtures/generateTransactionFixtures.ts (개발 전용, 5.2절)
    └── components/
        ├── TransactionTable.tsx
        ├── TransactionFilterBar.tsx
        └── columns.tsx
```

---

## 6. 단계별 구현 순서

### 6.1 baseline

실제 `/v2/transaction` API(cursor 기반, `size` 최대 100) + "더 보기"/무한스크롤(`CardTransactions.vue`와 동일한 이미 검증된 UX 패턴 재사용) + TanStack Table **비가상화(naive)** 렌더링. 이것이 실서비스 경로다.

### 6.2 측정 (실서비스 경로와 별개의 fixture 기반)

서버 `size` 상한이 100이라 **실제 API 응답만으로는 1k~100k 규모의 대량 렌더링 문제를 재현할 수 없다.** 이를 위해 실서비스 경로와 분리된 개발 전용 도구를 둔다:

- `src/features/transactionHistory/fixtures/generateTransactionFixtures.ts` — 1k / 10k / 30k / 100k개의 `TransactionResponse` 모양 가짜 데이터를 생성하는 개발 전용 fixture 생성기 (실제 API를 타지 않음, seed 기반 결정적 생성).
- `src/pages/TransactionBenchmarkPage.tsx` + 개발 전용 라우트(예: `/dev/transaction-benchmark`, production 빌드/라우팅에서 제외) — fixture 데이터 볼륨(1k/10k/30k/100k)을 선택하고 **같은 컴포넌트 트리 안에서 naive 렌더 ↔ 가상화 렌더를 토글**할 수 있는 벤치마크 화면. `TransactionTable`은 `virtualized: boolean` prop으로 두 모드를 모두 지원해, 실제 화면(baseline)과 벤치마크 화면이 같은 컬럼 정의/컴포넌트를 공유한다.

**측정 항목** (수치는 아직 채우지 않는다):

| 측정 항목 | 값 |
|---|---|
| DOM row 수 | _(미측정 — production 빌드에서 실측)_ |
| 초기 렌더 시간 | _(미측정)_ |
| 스크롤 프레임 (FPS/jank) | _(미측정)_ |
| JS heap 사용량 | _(미측정)_ |

측정은 사용자가 **production 빌드**(`npm run build` + `npm run preview` 등)에서 브라우저 DevTools(Performance/Memory 탭)로 직접 수행한다. 이 문서에는 수치를 추측해 채우지 않는다.

**결과 기록 위치**: 이 저장소의 기존 관례(`docs/benchmarks/*.md`)를 따라 `docs/benchmarks/frontend-transaction-table-rendering-<날짜>.md`에 위 측정 항목별 실측치를 기록한다 (아직 작성하지 않음, 자리만 정의).

### 6.3 가상화

측정 결과 필요 시(혹은 이미 벤치마크 라우트에서 검증된 방식대로) `@tanstack/react-virtual`을 TanStack Table 행 렌더 루프에 결합해 baseline 화면에도 적용할지 결정한다.

### 6.4 커스터마이징

거래유형/서브타입별 셀 렌더러(아이콘/색상), 상세 모달, 반응형/빈 상태/에러 상태 마감.

---

## 7. 변경/추가 예정 파일

**신규 (`xcp-front-react/`, 아직 생성 안 함)**:
- 프로젝트 스캐폴딩 전체 (5절 디렉터리 구조)
- `src/features/transactionHistory/fixtures/generateTransactionFixtures.ts`
- `src/pages/TransactionBenchmarkPage.tsx` (개발 전용 라우트)
- `.env.development` (`VITE_API_BASE_URL`)
- `xcp-front-react/CLAUDE.md` (8절 초안)

**결과 기록용 (값은 비워두고 자리만)**:
- `docs/benchmarks/frontend-transaction-table-rendering-<날짜>.md`

**조건부 — 이번 계획 범위 밖, 별도 승인 필요**:
- `src/main/java/bumblebee/xchangepass/global/security/SecurityConfig.java`의 CORS 허용 origin 추가 (2.7절)

---

## 8. xcp-front-react/CLAUDE.md 초안 (제안, 파일 미생성)

> 아래는 `xcp-front-react/` 프로젝트가 실제로 만들어질 때 그 디렉터리에 넣을 `CLAUDE.md` 초안이다. 이번 작업에서는 파일을 생성하지 않는다.

- **프로젝트 성격**: `xcp-front/`(Vue)와 별개의 React 전용 프로젝트. 현재는 거래내역 통합조회 화면 전담. 전체 마이그레이션 여부는 미정.
- **커맨드**: `npm install` / `npm run dev` / `npm run build` / `npm run lint` / `npm run test` (lint/test는 신규 프로젝트에 처음부터 구성 — ESLint+Prettier, Vitest+React Testing Library 제안. xcp-front에는 없던 것).
- **백엔드 연동 규칙**: base URL은 `VITE_API_BASE_URL` env로만 관리(하드코딩 금지 — xcp-front의 반복된 실수). 인증은 httpOnly 쿠키 기반이므로 axios는 항상 `withCredentials: true`. JWT를 절대 `localStorage`에 저장하지 않는다.
- **거래내역 API 계약 규칙**: `cursor`는 항상 opaque — 파싱/직접 구성 금지, 서버가 준 `nextCursor`를 그대로 echo. 다형성 거래 데이터의 discriminant는 `data.transactionType`을 쓰고 `@class`에 의존하지 않는다. wallet/card 거래에는 status 필드가 없다는 것을 전제로 UI를 설계한다.
- **백엔드 enum 동기화 지점**: `TransactionType` / `WalletTransactionType` / `CardTransactionType` / `TransactionDirection` (경로: `domain/transaction/entity/`, `domain/wallet/transaction/entity/`, `domain/cardTransaction/entity/`) — 백엔드 enum이 바뀌면 TS 쪽 리터럴 유니온도 함께 갱신한다.
- **아키텍처 규칙**: 서버 상태(TanStack Query)와 UI 상태(필터/모달)를 분리 유지. 컬럼 정의는 feature별로 한 파일에 모아 관리. 전체 프로젝트 TypeScript로만 작성(신규 `.js` 파일 추가 금지).
- **커스텀 셀 렌더러 규칙**: status 필드가 없는 wallet/card 거래는 `transactionType` + 서브타입(`walletTransactionType`/`cardTransactionType`/`direction`)을 기준으로 시각적으로 구분한다.
- **성능 검증 규칙**: `fixtures/`와 벤치마크 라우트(`/dev/...`)는 개발 전용이며 production 빌드/라우팅에서 제외한다. 벤치마크 결과 수치는 추측으로 채우지 않고 실측값만 `docs/benchmarks/`에 기록한다.

---

## 9. 실행 범위

이번 실행에서 만든 파일은 **이 문서(`docs/frontend-plan.md`) 1개뿐**이다. 프로젝트 스캐폴딩, 의존성 설치, 코드 생성, 백엔드 CORS 수정은 아직 수행하지 않았으며 각각 별도 승인 후 진행한다.

---

## 10. 실행 결과 — 스캐폴딩 단계에서 계획과 달라진 결정

`xcp-front-react/` 스캐폴딩을 실제로 진행하면서, 위 계획(3절 스택 결정, 2.7절 CORS 옵션) 중 아래 항목은 사용자 승인을 받아 다르게 확정됐다. 각 이유의 상세 근거는 `xcp-front-react/CLAUDE.md`의 "의존성 버전 관련 결정" / "Node 버전" / "Dev 서버 포트 / 프록시" 절 참고.

| 계획(3절) | 실제 결정 | 이유 |
|---|---|---|
| React 18 | **React 19** | `@tanstack/react-table`/`@tanstack/react-virtual`/`@testing-library/react`가 `npm view <pkg> peerDependencies`로 확인한 결과 모두 React 19를 peerDependency로 명시 지원 |
| react-router-dom v6 | **react-router-dom v7 (7.18.2)** | `npm audit` 결과 6.x 라인 전체(6.0.0~6.30.4)에 패치 없는 moderate CVE(open redirect/XSS) 존재, 패치는 7.18.2+에만 있음 |
| (미정, 최신 TypeScript 암묵 가정) | **TypeScript 5.9.3 고정** (npm `latest`=7.0.2 Go 컴파일러는 사용 안 함) | `typescript-eslint`의 peerDependency가 `>=4.8.4 <6.1.0`이라 TS7 미지원 |
| — (계획 범위 밖 항목) | **로컬 개발 Node 버전을 v24 LTS로 고정**(`.nvmrc`) | Node v23(비-LTS) + npm 10.9.2 조합에서 `npm install`이 `@npmcli/arborist`의 optional peer 순환 처리 버그(`Cannot read properties of null (reading 'edgesOut')`)로 실패. 우리 의존성 충돌이 아니라 npm 자체 버그였고, Node 24 LTS(번들 npm 11.x)로 전환 후 `--legacy-peer-deps` 없이 정상 설치됨 |
| 2.7절: "포트를 3000에 맞추거나 CORS 허용 목록에 백엔드 코드 수정으로 추가" 중 택1 | **둘 다 아님 — Vite dev proxy로 우회** | `vite.config.ts`의 `server.proxy['/api'] → http://localhost:8080`(경로에서 `/api` 제거)로 브라우저 입장에서 동일 출처가 되게 해서, 포트를 3000에 맞출 필요도 없고 백엔드 `SecurityConfig`의 CORS 허용 목록을 건드릴 필요도 없어졌다. `xcp-front-react`는 계획대로 5174(=Vite 기본값 5173을 쓰는 `xcp-front`와 겹치지 않는 포트)를 그대로 씀 |

`@tanstack/react-table`/`@tanstack/react-virtual`은 4.3절 결론대로 이 스캐폴딩 단계에서 함께 설치했다(원래 계획은 6절 "단계별 구현 순서"에서 나중에 넣는 뉘앙스였으나, 핵심 라이브러리라는 사용자 판단으로 앞당김).
