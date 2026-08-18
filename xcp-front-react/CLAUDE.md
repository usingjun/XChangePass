# CLAUDE.md (xcp-front-react)

`xcp-front/`(Vue)와 별개인 React 전용 프로젝트. 지금은 거래내역 통합조회 화면 전담 스캐폴딩 단계이며, 화면 자체는 아직 구현하지 않았다. 이 프로젝트를 둘 다 건드리는 작업이 아니라면 `xcp-front/`는 수정하지 않는다. 배경/조사 근거는 저장소 루트 `docs/frontend-plan.md` 참고.

## Node 버전 — 반드시 LTS(v24) 사용

`.nvmrc`에 `24.19.0`이 명시돼 있다. **Node v23(홀수 Current 라인) + npm 10.9.2 조합에서는 `npm install`이 실패한다** — `vitest`의 optional peer 사슬(`@vitest/browser` ↔ `webdriverio` ↔ `@vitest/ui`, 순환 구조)을 resolve하다가 `@npmcli/arborist`가 `TypeError: Cannot read properties of null (reading 'edgesOut')`로 죽는 npm 버그다(우리 의존성 버전 충돌이 아님). Node 24 LTS로 전환하면 번들 npm이 11.x대로 올라가면서 `--legacy-peer-deps`/`--omit=optional` 없이 깨끗하게 설치된다. `fnm use`(또는 `nvm use`)로 `.nvmrc`를 따라간 뒤 `npm install`.

**AI 에이전트/CI처럼 비대화형 셸에서 명령을 실행할 때 주의**: `fnm`은 `~/.zshrc`의 `eval "$(fnm env --use-on-cd)"` 훅으로 PATH를 바꾸는데, 이 훅은 대화형 로그인 셸에서만 실행된다. 비대화형 셸(예: Claude Code의 Bash 도구, 스크립트 러너)은 이 훅을 안 타서 `node -v`/`npm -v`가 `fnm`으로 전환한 버전이 아니라 Homebrew가 설치한 시스템 Node(예: v23.x, `/opt/homebrew/bin/node`)를 계속 가리킬 수 있다. 이런 셸에서는 이 디렉터리의 `npm`/`node`/`npx` 명령 앞에 매번 `eval "$(fnm env)"`를 붙인다:

```bash
eval "$(fnm env)" && npm install
eval "$(fnm env)" && npm run dev
```

`fnm env`는 `.nvmrc`가 있는 디렉터리에서 실행하면 그 파일이 요구하는 버전(`24.19.0`)을 기준으로 PATH를 잡아준다. `node -v`로 `v24.19.0`이 나오는지 매번 확인하는 게 안전하다.

## 커맨드

```bash
npm install
npm run dev          # http://localhost:5174
npm run build         # tsc -b && vite build
npm run preview
npm run lint          # eslint .
npm run format         # prettier --write .
npm run format:check
npm run typecheck      # tsc -b --noEmit
npm run test           # vitest run
npm run test:watch     # vitest
npm run test:e2e       # playwright test (테스트 파일은 아직 없음 — e2e/ 디렉터리부터 만들 것)
```

Playwright 브라우저 바이너리는 아직 설치 안 했다. 첫 e2e 테스트를 작성하기 전에 `npx playwright install`을 한 번 실행해야 한다.

## Dev 서버 포트 / 프록시

- **포트 5174.** `xcp-front/`(Vue)가 `vite.config.js`에 포트를 지정하지 않아 Vite 기본값 5173을 그대로 쓰고 있어서, 두 프론트를 동시에 띄울 수 있도록 다른 포트로 정했다.
- `vite.config.ts`의 `server.proxy['/api']`가 `http://localhost:8080`으로 프록시하면서 `/api` 접두사를 벗겨낸다(백엔드 실제 엔드포인트는 `/v2/transaction`처럼 `/api` 없이 노출됨). 브라우저 입장에서는 5174 하나의 오리진으로만 보이므로 CORS가 발생하지 않는다.
- 이 덕분에 백엔드 `SecurityConfig`의 CORS 허용 목록(`cors.front.url=http://localhost:3000`)을 건드릴 필요가 없다 — `docs/frontend-plan.md` 2.7절이 고민하던 "포트를 3000에 맞추거나 CORS 목록에 추가" 딜레마를 프록시로 우회한 것.
- `.env.development`의 `VITE_API_BASE_URL=/api`가 이 프록시 경로를 가리킨다. 실제 배포 환경에서는 `VITE_API_BASE_URL`을 백엔드 오리진으로 바꿔서 쓴다(값은 항상 이 env로만 관리 — 하드코딩 금지, `xcp-front`가 4곳에 `http://localhost:8080`을 박아넣었던 실수를 반복하지 않는다).

## 백엔드 연동 규칙

- 인증은 httpOnly 쿠키(`accessToken`) 기반. `src/lib/apiClient.ts`의 axios 인스턴스는 항상 `withCredentials: true`. JWT를 `localStorage`에 저장하지 않는다.
- `cursor`는 항상 opaque 문자열이다 — 파싱하거나 직접 구성하지 않고, 서버가 준 `nextCursor`를 다음 요청의 `cursor`에 그대로 echo한다.
- 다형성 거래 데이터(`TransactionResponse.data`)의 판별 필드는 `data.transactionType`(`WALLET`/`CARD`/`EXCHANGE`)을 쓴다. 백엔드가 Jackson `@JsonTypeInfo(use=Id.CLASS)`로 내려주는 `"@class"`(Java 클래스 풀네임)는 절대 파싱/의존하지 않는다.
- wallet/card 거래에는 상태(status) 필드가 응답에 없다(exchange만 내부적으로 COMPLETED로 필터링돼 나옴). "거래 상태" UI를 전제로 설계하지 않는다 — 대신 `transactionType` + 서브타입(`walletTransactionType`/`cardTransactionType`/`direction`)으로 시각적으로 구분한다.
- 금액 필드(`amount`, `balanceAfter`, `beforeAmount`, `afterAmount`, `rate`)는 BigDecimal 직렬화 값이라 TS 타입에서 `number`가 아니라 `string`으로 다룬다(`src/features/transactionHistory/types.ts` 참고). 실제 계산이 필요해지면 그 시점에 정밀도 손실 여부를 다시 확인한다.

## 백엔드 enum 동기화 지점

`TransactionType`(`domain/transaction/entity/`), `TransactionDirection`(동일), `WalletTransactionType`(`domain/wallet/transaction/entity/`), `CardTransactionType`(`domain/cardTransaction/entity/`) — 백엔드에서 값이 추가/변경되면 `src/features/transactionHistory/types.ts`의 리터럴 유니온도 함께 갱신한다.

## 의존성 버전 관련 결정 (스캐폴딩 당시 이유)

- **React 19** (18이 아님): `@tanstack/react-table`(`react: '>=18'`), `@tanstack/react-virtual`(`react: '^16.8.0 || ^17.0.0 || ^18.0.0 || ^19.0.0'`), `@testing-library/react`(`^18.0.0 || ^19.0.0`) 모두 19를 peerDependency로 명시 지원해서 최신으로 맞췄다.
- **react-router-dom은 v7**(계획 초안은 v6이었음): 6.x 라인 전체(6.0.0~6.30.4)에 moderate severity open-redirect/XSS CVE가 있고 패치가 7.18.2+에만 존재해서 v7로 올렸다. declarative 라우팅(이번에 쓰는 기본 `createBrowserRouter` 사용법)은 v6과 API가 거의 동일하다.
- **TypeScript는 5.9.3 고정** (npm `latest` 태그는 7.0.2인 TS7 Go 컴파일러이지만 사용하지 않음): `typescript-eslint`의 peerDependency가 아직 `typescript: '>=4.8.4 <6.1.0'`라 TS7을 지원하지 않는다.
- **ESLint flat config 함정**: `eslint-plugin-react-hooks@7.x`의 `configs['recommended-latest']`는 legacy eslintrc 스타일(`plugins: ["react-hooks"]` 배열)이라 flat config(ESLint 10)에서 에러가 난다. 반드시 `configs.flat.recommended`(plugins가 객체 형태)를 써야 한다 — `eslint.config.js` 참고.

## 아키텍처 규칙

- 서버 상태(TanStack Query 캐시)와 UI 상태(필터/모달 등 로컬 state)를 분리 유지한다.
- 컬럼 정의는 feature별로 한 파일(`columns.tsx`)에 모아 관리한다(아직 미작성 — 거래내역 화면 구현 단계에서 추가).
- 전체 프로젝트 TypeScript로만 작성한다. 새 `.js` 파일을 추가하지 않는다.
- `src/features/transactionHistory/fixtures/`와 `/dev/...` 벤치마크 라우트는 개발 전용이며 production 빌드/라우팅에서 제외한다(아직 미작성). 벤치마크 결과 수치는 추측으로 채우지 않고 실측값만 `docs/benchmarks/`에 기록한다.

## 디렉터리

```
src/
├── lib/apiClient.ts          # axios 인스턴스 (withCredentials, baseURL=VITE_API_BASE_URL)
├── router/index.tsx          # createBrowserRouter 라우트 정의
├── pages/HomePage.tsx        # 부팅 확인용 최소 페이지
└── features/transactionHistory/
    └── types.ts              # TransactionResponse/TransactionPageResponse/서브 DTO/enum 타입
```

거래내역 화면(`TransactionTable`, `TransactionFilterBar`, `columns.tsx`, `useTransactionHistoryQuery` 등)은 다음 단계에서 추가한다.
