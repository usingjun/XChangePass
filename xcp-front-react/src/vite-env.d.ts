/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  // 측정용 production 빌드에만 벤치마크 라우트를 포함시키기 위한 플래그.
  // 'true' 문자열일 때만 켠다 — VITE_ENABLE_BENCHMARK=true npm run build:benchmark.
  readonly VITE_ENABLE_BENCHMARK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
