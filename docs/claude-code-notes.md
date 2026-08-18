# Claude Code 사용 기록 — 거래내역 React 화면 작업

`xcp-front-react/` 신규 프로젝트 작업 과정에서 Claude Code를 어떻게 썼는지, 왜 그 기능이 필요했는지, 실제로 무슨 일이 있었는지, 뭐가 아쉬웠는지를 단계별로 남긴다. 기능 소개가 아니라 이 프로젝트에서 실제로 겪은 문제와 대응 위주로 적는다.

---

## 0단계 — `/init`으로 CLAUDE.md 생성

**사용한 기능**: `/init` 슬래시 커맨드. 저장소를 스캔해 루트 `CLAUDE.md` 초안을 자동 생성한다.

**왜 필요했는지**: 매 세션마다 프로젝트 구조·도메인·빌드 커맨드·위험 영역(지갑/이체/사기탐지 등)을 처음부터 설명하지 않아도 되게, 저장소에 영속적인 컨텍스트 파일을 만들어두기 위해서.

**실제로 있었던 일**: 결과물은 `bumblebee.xchangepass` 패키지 구조, 도메인 목록, wallet/transfer/fraud 등 고위험 영역, `./gradlew test` 같은 빌드/테스트 커맨드는 정확히 잡아냈다. 하지만 저장소 루트에 있는 `xcp-front/`(Vue 3 프론트엔드)는 생성된 CLAUDE.md 어디에도 언급되지 않았다 — 문서 전체가 Java/Gradle 백엔드 관점으로만 쓰였다.

**아쉬웠던 점**: `/init`이 저장소 최상위 디렉터리 전체를 훑었다면 `xcp-front/`의 존재와 그게 Vue라는 사실은 바로 드러났을 텐데, 결과가 백엔드 소스 트리 쪽에 편중됐다. 그 결과 이후 세션에서 "`xcp-front/`에 React 거래내역 화면을 추가해달라"는 요청이 들어왔을 때, CLAUDE.md만 봐서는 그 요청의 전제(React 프로젝트라는 가정)가 틀렸다는 걸 미리 알 수 없었다. 프론트엔드가 여러 개 있거나 이질적인 스택이 섞인 저장소에서는 `/init` 직후 "이 저장소에 프론트엔드가 있는가, 있다면 무슨 스택인가"를 사람이 한 번 검증하는 과정이 필요해 보인다.

---

## 1단계 — Plan mode 조사

**사용한 기능**: Plan mode. 파일을 수정하지 않고 코드/설정을 읽기만 하면서 실행 계획을 세우는 모드.

**왜 필요했는지**: "`xcp-front/`에 React 거래내역 통합조회 화면을 추가"라는 요청을 그대로 실행하기 전에, 그 디렉터리가 실제로 어떤 스택인지, 붙일 백엔드 API(`/v2/transaction`)의 계약이 뭔지부터 확인해야 손을 대도 되는지, 댄다면 어떻게 대야 하는지 판단할 수 있었기 때문.

**실제로 있었던 일**: 조사 결과 `xcp-front/`는 Vue 3 + Vite였고(React 아님), TypeScript도 아니고(`jsconfig.json`뿐), 서버 상태 라이브러리도 없고, 테이블/그리드 컴포넌트도 없어서 원 요청의 전제 자체가 틀렸다는 게 드러났다. 이 불일치를 사용자에게 보고하고 확인받은 뒤, "기존 Vue 프로젝트를 건드리지 않고 별도의 `xcp-front-react/` 신규 프로젝트를 만든다"는 방향으로 계획 자체가 바뀌었다(경위는 `docs/frontend-plan.md` 0절에 기록). 같은 조사 단계에서 백엔드 `/v2/transaction` 응답 구조, 커서 기반 페이지네이션, CORS 설정, Jackson `@class` discriminant 문제 등도 실제 코드를 읽어서 확인했고, 이 내용이 계획 문서의 근거가 됐다.

**아쉬웠던 점**: Plan mode 자체는 의도대로 동작했다 — 코드를 안전하게 읽기만 하면서 계획을 세웠고, 코드/설정 변경은 전혀 없었다. 다만 사용자 요청 문구("xcp-front/에 React 화면 추가")를 곧장 실행 계획에 반영하기보다, 계획을 세우기 전에 "이 디렉터리가 정말 React인가" 같은 최소한의 전제 검증을 자동으로 먼저 하는 습관이 있었다면 이 조사가 더 빨리 시작됐을 것 같다. `/init` 결과물이 애초에 프론트엔드 존재를 알려주지 않았던 것(0단계)도 이 지연에 한몫했다.

---

## 1.5단계 — 스캐폴딩

**사용한 기능**: 위험도가 있는 쓰기/설치 작업마다 실행 전에 승인을 구하는 권한 승인 모드, 그리고 코드를 작성하기 전에 `npm view <pkg> peerDependencies`/`npm audit`로 실제 호환성·보안 상태를 확인하고 나서만 진행하는 조사 패턴.

**왜 필요했는지**: 계획 문서(`docs/frontend-plan.md`)에 적힌 버전 결정(React 18, react-router-dom v6 등)은 실제로 설치해보기 전까지는 검증되지 않은 가정이었다. 새 프로젝트 스캐폴딩은 의존성 트리·lockfile·여러 설정 파일이 한 번에 생기는, 되돌리기 번거로운 작업이라 각 결정 지점(의존성 목록, 포트, 버전)마다 사용자 확인을 받는 게 맞다고 판단했다.

**실제로 있었던 일**: 확인 과정에서 계획과 다른 결정이 여러 차례 나왔다.
- `@tanstack/react-table`/`@tanstack/react-virtual`이 React 19를 peerDependency로 명시 지원한다는 게 `npm view`로 확인되면서, 계획의 React 18 대신 19로 감.
- `npm audit` 결과 `react-router-dom` v6 라인 전체(6.0.0~6.30.4)에 패치 안 된 moderate CVE(open redirect/XSS)가 있는 게 드러나 v7(7.18.2)로 전환.
- `typescript-eslint`가 아직 TypeScript 7(Go 컴파일러, npm `latest` 태그)을 지원하지 않는 걸 peerDependency 조회로 확인하고 5.9.3으로 고정.
- ESLint 10 flat config에서 `eslint-plugin-react-hooks@7`의 `configs['recommended-latest']`가 legacy eslintrc 포맷(`plugins` 배열)이라 즉시 에러가 나서 `configs.flat.recommended`로 교체.

여기에 환경 문제도 두 번 겹쳤다.
- `npm install`이 npm/arborist의 optional peer 순환 의존성(vitest의 브라우저 모드 관련 optional peer들) 처리 버그로 계속 죽었다. `--legacy-peer-deps`로 우회할 수도 있었지만, 사용자가 "임시 우회 전에 원인부터 확인하자"고 해서 로그를 분석해 npm 버그라는 걸 확인했고, Node를 비-LTS인 v23에서 v24 LTS로 전환(사용자가 직접 `fnm`으로 전환)한 뒤 문제 없이 해결됐다.
- Node 전환 후에도 Claude Code의 Bash 도구가 `~/.zshrc`에 등록된 `fnm`의 셸 훅(`eval "$(fnm env --use-on-cd)"`)을 타지 않아서, `node -v`/`npm -v`가 여전히 Homebrew 시스템 Node(v23.x)를 가리키는 걸 발견했다. 매 명령 앞에 `eval "$(fnm env)"`를 직접 붙여야 한다는 걸 알아내서 `xcp-front-react/CLAUDE.md`에 기록해뒀다.

**아쉬웠던 점**: 승인 요청이 잦았던 건(의존성 목록, 포트, React 버전, 라우터 버전) 결과적으로 다 타당한 이유가 있었지만, `npm audit`/`npm view` 조사를 먼저 다 끝내고 한 번에 종합해서 물어봤으면 왕복 횟수를 줄일 수 있었을 것 같다 — `react-router-dom`의 CVE는 실제로 v6로 한 번 설치를 마친 뒤에야 `npm audit`으로 발견됐는데, 처음 버전을 제안하는 시점에 보안 advisory까지 미리 확인했다면 처음부터 v7로 제안했을 것이다. 또 Bash 도구가 비대화형이라 `fnm`/`nvm` 셸 훅을 안 태운다는 사실은 어디에도 문서화돼 있지 않아서, 실패를 직접 겪고 나서야 알아냈다 — 이런 셸 초기화 차이는 도구 쪽에서 미리 알려줬으면 더 빨리 진단할 수 있었을 부분이다.

---

## 2단계 — PostToolUse Hook으로 lint/typecheck 강제

**사용한 기능**: `.claude/settings.json`의 `PostToolUse` Hook. `Write`/`Edit` 도구 호출이 끝난 직후 하네스(도구 실행 레이어)가 무조건 실행하는 셸 커맨드.

**CLAUDE.md(지시)와 Hook(강제)의 차이**: `xcp-front-react/CLAUDE.md`에는 이미 "코드 수정 후 lint/typecheck 실행"이라는 문구가 있었지만, CLAUDE.md는 모델 컨텍스트에 주입되는 지시문일 뿐이다 — 모델이 그 지시를 매번 스스로 상기하고 "따르기로 판단"해야 실행된다. 컨텍스트가 길어지거나, 한 턴에 여러 파일을 연속으로 고치거나, 대화가 압축(compact)되면 지켜지지 않을 수 있는 권고 수준이다. 반면 Hook은 모델의 판단을 거치지 않는다 — Edit/Write 도구 호출이 성공하면 모델이 그 사실을 "기억"하고 있는지와 무관하게 하네스가 등록된 커맨드를 실행한다. CLAUDE.md는 "이렇게 해줘"이고 Hook은 "이건 무조건 일어난다"는 차이다. 다만 강제되는 건 "실행 여부"뿐이다 — 지금 구성은 lint/typecheck를 그냥 돌리고 종료 코드로 결과를 남기는 수준이라, 실패를 모델이 반드시 그 자리에서 보고 고치게 만드는 것(hook의 `decision: "block"` 출력)까지는 하지 않았다.

**왜 이 시점에 도입했는지**: 직전 세션들(auto/fixed layout 토글, 6.3 가상화)에서 한 요청 안에 `TransactionTable.tsx`/`columns.tsx`/`setupTests.ts`/테스트 파일 여러 개를 연속으로 고치는 멀티파일 변경이 반복됐다. 매번 마지막에 수동으로 `npm run lint && npm run typecheck`를 실행하긴 했지만, 이건 "실행하는 걸 잊지 않았기 때문"이지 구조적으로 보장된 결과가 아니었다 — CLAUDE.md 지시문이 그 세션에서 우연히 지켜진 것이지, 다음 세션·다음 대화에서도 지켜진다는 보장은 없다. 한 턴에 손대는 파일 수가 늘어날수록 "다 고치고 나서 검증을 건너뛸" 여지도 함께 늘어나므로, CLAUDE.md에 지시를 더 강한 문구로 다시 적는 대신 이 시점에 Hook으로 전환해 매 Edit/Write 직후 자동으로 검증되게 만들었다.

**실제로 있었던 일**: `.claude/settings.json`(프로젝트 공유 설정 — git에 커밋되는 파일이라 개인 전용인 `settings.local.json`과는 분리)에 `PostToolUse` Hook을 새로 추가했다(기존에 등록된 Hook은 없었다). 매처는 `Write|Edit`, 커맨드는 `tool_input.file_path`가 `xcp-front-react/` 아래인지 `jq`로 먼저 걸러낸 뒤에만 `cd xcp-front-react && eval "$(fnm env)" && npm run lint && npm run typecheck`를 실행한다 — 그 디렉터리 밖(백엔드 Java, `docs/` 등)을 고칠 때는 아무 것도 실행되지 않는다. `eval "$(fnm env)"`가 필요한 이유는 `xcp-front-react/CLAUDE.md`에 이미 기록돼 있던 것과 같은 문제다: Bash 도구는 비대화형 셸이라 `~/.zshrc`의 `fnm` 훅을 안 타서, 이게 없으면 Homebrew 시스템 Node(v23)로 실행돼 이 프로젝트가 요구하는 Node 24 LTS 전제가 깨진다.

동작 확인은 두 단계로 했다. 먼저 실제 Hook이 받는 것과 같은 JSON을 stdin으로 직접 파이프해 커맨드 자체가 의도대로 동작하는지 확인했고(정상적으로 lint/typecheck 실행, `xcp-front-react/` 밖 파일 경로는 8ms 만에 no-op으로 빠짐), 그다음 커맨드 맨 앞에 sentinel 로깅(`echo ... >> /tmp/claude-hook-check.txt`)을 임시로 붙여 실제 `.gitignore` 파일에 대한 진짜 Edit 도구 호출로 트리거해봤다. 세션 시작 시점에는 `.claude/settings.json` 자체가 존재하지 않았는데도(그 디렉터리에 `settings.local.json`만 있었다) Hook이 곧바로 잡혔다. sentinel과 검증용으로 `.gitignore`에 잠깐 추가했던 주석 줄은 확인 직후 모두 원복했다.

**아쉬웠던 점**: 이 Hook은 파일 하나를 고칠 때마다 lint(전체 파일 대상 ESLint) + typecheck(`tsc -b`, 프로젝트 전체 참조 빌드) 전체를 다시 돈다. 이번 작업 범위(한 턴에 파일 몇 개)에서는 몇 초 수준이라 문제없었지만, 같은 세션에서 짧은 간격으로 훨씬 많은 파일을 연속 수정하게 되면 매번 전체 재검사가 누적돼 체감 지연이 커질 수 있다 — 파일 수가 크게 늘어나면 "이번에 바뀐 파일만" 보는 lint-staged류 구성으로 좁히는 걸 고려할 만하다. 또 지금은 실패해도 그냥 종료 코드만 남기고 넘어가므로, 실패를 사람이 스크롤을 올려 직접 확인해야 알아챈다 — 정말 "강제"하려면 실패 시 `decision: "block"`으로 모델 턴에 사유를 주입해 그 자리에서 고치게 만드는 단계가 다음으로 남아 있다.
