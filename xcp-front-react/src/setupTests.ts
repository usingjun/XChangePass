import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// vitest.config의 test.globals가 false라 @testing-library/react의 자동 cleanup(afterEach 전역 감지)이
// 동작하지 않는다. 파일당 테스트가 하나뿐일 때는 안 보이던 문제라 명시적으로 등록해둔다.
afterEach(cleanup)

// jsdom은 IntersectionObserver를 구현하지 않는다. 무한 스크롤 sentinel을 쓰는 컴포넌트를
// 렌더링만 해도 참조 에러가 나므로 아무 동작도 하지 않는 최소 스텁을 등록해둔다.
class IntersectionObserverStub implements IntersectionObserver {
  readonly root: Element | Document | null = null
  readonly rootMargin: string = ''
  readonly thresholds: ReadonlyArray<number> = []
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return []
  }
}

vi.stubGlobal('IntersectionObserver', IntersectionObserverStub)
