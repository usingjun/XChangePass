/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // xcp-front(Vue)가 Vite 기본 포트 5173을 그대로 쓰고 있어(vite.config.js에 별도 지정 없음),
    // 두 프론트를 동시에 띄울 수 있도록 5174를 쓴다.
    port: 5174,
    strictPort: true,
    proxy: {
      // 브라우저 기준 동일 출처(5174)로 요청하게 해 CORS를 피한다.
      // 백엔드 CORS 허용 목록(cors.front.url=http://localhost:3000)은 건드리지 않는다.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    globals: false,
  },
})
