import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// vitest.config의 test.globals가 false라 @testing-library/react의 자동 cleanup(afterEach 전역 감지)이
// 동작하지 않는다. 파일당 테스트가 하나뿐일 때는 안 보이던 문제라 명시적으로 등록해둔다.
afterEach(cleanup)
