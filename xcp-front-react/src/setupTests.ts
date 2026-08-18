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

// jsdom은 실제 CSS 레이아웃 엔진이 없어 offsetHeight가 항상 0이다. @tanstack/react-virtual은
// 스크롤 컨테이너와 각 행의 세로 크기를 offsetHeight로 측정해 가상 스크롤 범위(어떤 행을 DOM에
// 마운트할지)를 계산하므로, 0이 계속 나오면 데이터 크기와 무관하게 행이 하나도(또는 거의) 렌더링되지
// 않아 가상화 테스트가 실제 동작과 무관하게 깨진다. inline style에 height가 있으면(TransactionTable의
// 스크롤 컨테이너처럼) 그 값을, 없으면(측정 대상인 개별 행) 고정 추정치를 돌려주는 최소 스텁을 건다.
Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
  configurable: true,
  get() {
    const inlineHeight = Number.parseFloat(this.style.height)
    return Number.isNaN(inlineHeight) ? 36 : inlineHeight
  },
})
