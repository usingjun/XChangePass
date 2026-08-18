---
name: render-benchmark
description: Use when measuring or recording frontend rendering performance (Scripting/Rendering/Painting time, DOM row count, Frames, Heap) for the transaction table or similar screens in xcp-front-react — covers the production-build measurement protocol and the docs/benchmarks/ recording format this project uses. Never fabricate, estimate, or round measurement numbers.
---

# 렌더링 성능 벤치마크

`xcp-front-react`의 렌더링 성능(특히 `TransactionTable`)을 측정하고 `docs/benchmarks/`에 기록할 때 따르는 절차. 이 Skill은 숫자를 대신 만들어내라는 게 아니라, **사람이 직접 측정한 숫자를 정확한 형식으로 기록**하기 위한 것이다.

## 0. 가장 중요한 원칙: 수치는 사람이 측정한다

- Claude는 Chrome DevTools Performance 탭을 직접 열 수 없다. **Scripting/Rendering/Painting/Frames/Heap 수치를 추측·계산·보간해서 채우지 않는다.**
- 사용자가 "측정 완료했다"며 표를 주면 그 값을 **보정하지 않고 그대로** 기록한다 — 반올림하거나 "이상치니까 빼자"고 판단하지 않는다. 노이즈로 보이는 값(예: 데이터가 더 큰 쪽이 더 빠르게 나온 경우)이 있으면 "관찰" 절에 노이즈 가능성을 적어두되, 값 자체는 고치지 않는다.
- 아직 측정되지 않은 상태에서 "벤치마크 기록해줘"라는 요청을 받으면 실측값이 없다는 뜻이다 — 그럴듯한 숫자를 지어내지 말고, 사용자에게 먼저 측정해달라고 요청하거나 Claude가 화면을 직접 실행/측정할 수단이 있을 때만 그걸로 실측한다.

## 1. 측정 환경 — production 빌드로만

개발 서버(`npm run dev`)는 React 개발 모드 오버헤드(state 업데이트마다 추가 검증, 미압축 번들)가 섞여 실제 체감과 다른 숫자가 나온다. 반드시 production 빌드로 측정한다:

```bash
cd xcp-front-react
npm run build:benchmark && npm run preview:benchmark
# http://localhost:4173/dev/transaction-benchmark 에서 데이터 볼륨/토글 선택 후
# Chrome DevTools Performance 탭으로 기록
```

`build:benchmark`는 `VITE_ENABLE_BENCHMARK=true --mode benchmark`로 벤치마크 전용 라우트(`/dev/transaction-benchmark`)를 포함한 production 빌드를 만든다. 일반 `npm run build`는 이 라우트를 트리셰이킹으로 빼버리므로(`router/index.tsx`의 `import.meta.env.DEV` 분기) 측정에 쓸 수 없다.

측정 브라우저 조건:
- Chrome, **시크릿 창**, 확장 프로그램 없음 — 확장 프로그램의 콘텐츠 스크립트/옵저버가 Scripting 시간에 섞여 들어간다.
- CPU 스로틀링 없음.
- 데이터는 `generateTransactionFixtures`(seed 기반 결정적 생성)로 만든다 — 실제 API를 호출하지 않으므로 네트워크 지연이 측정치에 섞이지 않는다.

## 2. 측정 항목

Chrome DevTools Performance 탭 기록 후 Summary에서 읽는다:

- **DOM 행** — Elements 탭에서 `<tbody>` 안 실제 `<tr>` 개수. 가상화를 켰을 때는 전체 데이터 수가 아니라 **실제 마운트된 행 수**를 센다 — 이게 이 지표를 기록하는 이유다(전체 데이터 수와 DOM 행 수가 분리됐는지 확인하는 용도).
- **Scripting** — JS 실행 시간(React 렌더/reconciliation, 셀 포맷터 호출 등).
- **Rendering** — 스타일 계산 + 레이아웃(리플로우) 시간.
- **Painting** — 페인트 시간. 값이 있으면 항상 기록한다.
- **Frames** — Summary 하단의 총 프레임 시간(사용자 체감과 가장 가까운 수치).
- **Heap** — Memory 탭 또는 Performance 기록 중 JS Heap 크기(MB). 탭 크래시가 나면 크래시 직전 값과 "탭 크래시" 사실, Chrome 오류 코드(있으면)를 같이 적는다.

표 컬럼 순서는 이 프로젝트 기존 문서와 맞춘다: `데이터 | DOM 행 | Scripting | Rendering | 합계 | Frames | Heap`. `Painting`/`System`처럼 항상 나오는 게 아닌 항목은 값이 있을 때만 열을 추가한다 — 없는 값을 0이나 "-"로 지어내지 않는다.

## 3. docs/benchmarks/에 날짜별로 기록

- 파일명: `docs/benchmarks/<주제>-<YYYY-MM-DD>.md` (예: `frontend-transaction-table-rendering-2026-08-18.md`). raw 데이터가 있으면 같은 이름의 `.csv`를 나란히 둔다.
- 같은 날 같은 주제로 여러 번 측정하면(auto vs fixed, 가상화 전/후 등) 새 파일을 만들지 않고 **같은 날짜 파일에 `##` 섹션을 이어붙인다** — 이번 작업에서도 "주요 결과" → "table-layout: auto vs fixed 비교" → "가상화 적용 후" 순서로 한 파일에 계속 추가했다.
- 문서 구조 템플릿:

  ```markdown
  # <주제> 벤치마크

  ## 측정 환경
  (production 빌드 커맨드, 브라우저 조건, 데이터 생성 방식, 화면 경로)

  ## 재현 방법
  ​```bash
  (그대로 복붙하면 재현되는 커맨드)
  ​```

  ## 주요 결과
  | 데이터 | DOM 행 | Scripting | Rendering | 합계 | Frames | Heap |
  |---|---|---|---|---|---|---|
  ...

  ## 관찰 사항
  - (수치에서 읽을 수 있는 사실. 해석은 적되 숫자 자체는 바꾸지 않는다)
  ```

- 전/후 비교(가상화 적용 전/후, table-layout auto/fixed 등)를 기록할 때는 "전"/"후" 또는 "auto"/"fixed" 열을 나란히 두고, 각각이 **별도의 단발 측정 실행값**이라는 걸 본문에 명시한다 — 두 값을 같은 프로파일링 세션에서 뽑은 것처럼 쓰지 않는다.
- 노이즈로 보이는 결과(데이터가 더 큰데 더 빠르게 나온 경우 등)는 "관찰" 절에 "단일 측정 노이즈일 수 있음"이라고 적되, 다른 지표(예: DOM 행 수가 두 경우 다 같은 값으로 고정됨)로 결론이 여전히 성립하는지 같이 확인해서 함께 적는다.
