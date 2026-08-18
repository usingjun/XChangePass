import type {
  CardTransactionType,
  TransactionDataDto,
  TransactionDirection,
  TransactionResponse,
  TransactionType,
  WalletTransactionType,
} from '../types'

// 개발 전용 fixture 생성기. 실제 API를 타지 않고, 실서비스 응답(size 상한 100)으로는
// 재현할 수 없는 1k~100k 규모 렌더링 문제를 재현하기 위한 데이터를 만든다.
// seed가 같으면 항상 같은 데이터를 만든다(결정적 생성) — mulberry32 PRNG 사용.

function mulberry32(seed: number): () => number {
  let a = seed
  return function random() {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const CURRENCIES = ['KRW', 'USD', 'JPY', 'EUR'] as const
const TRANSACTION_TYPES: readonly TransactionType[] = ['WALLET', 'CARD', 'EXCHANGE']
const WALLET_TYPES: readonly WalletTransactionType[] = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER']
const DIRECTIONS: readonly TransactionDirection[] = ['SENT', 'RECEIVED']
const CARD_TYPES: readonly CardTransactionType[] = ['PAYMENT', 'DEPOSIT', 'REFUND']
const MERCHANTS = ['스타벅스', '이마트', '올리브영', '쿠팡', '배달의민족'] as const

// 실제 현재 시각과 무관하게 항상 같은 기준 시각에서 거슬러 올라가야 결정적 생성이 된다.
const BASE_TIME_MS = Date.parse('2026-01-01T00:00:00.000Z')
const INTERVAL_MS = 60_000

function pick<T>(items: readonly T[], rand: () => number): T {
  return items[Math.floor(rand() * items.length)]
}

function randomAmount(rand: () => number, max: number): string {
  return (Math.floor(rand() * max * 100) / 100).toFixed(2)
}

function buildData(transactionType: TransactionType, rand: () => number): TransactionDataDto {
  switch (transactionType) {
    case 'WALLET':
      return {
        transactionType: 'WALLET',
        counterpartyUserId: Math.floor(rand() * 1000) + 1,
        amount: randomAmount(rand, 500_000),
        direction: pick(DIRECTIONS, rand),
        walletType: pick(WALLET_TYPES, rand),
      }
    case 'CARD':
      return {
        transactionType: 'CARD',
        merchant: pick(MERCHANTS, rand),
        amount: randomAmount(rand, 200_000),
        balanceAfter: randomAmount(rand, 1_000_000),
        cardType: pick(CARD_TYPES, rand),
      }
    case 'EXCHANGE':
      return {
        transactionType: 'EXCHANGE',
        beforeAmount: randomAmount(rand, 1_000_000),
        afterAmount: randomAmount(rand, 1_000_000),
        rate: (rand() * 2 + 0.1).toFixed(4),
      }
  }
}

export function generateTransactionFixtures(count: number, seed = 1): TransactionResponse[] {
  const rand = mulberry32(seed)
  const items: TransactionResponse[] = new Array(count)

  for (let i = 0; i < count; i += 1) {
    const transactionType = pick(TRANSACTION_TYPES, rand)
    const beforeCurrency = pick(CURRENCIES, rand)
    const data = buildData(transactionType, rand)
    const afterCurrency =
      transactionType === 'EXCHANGE'
        ? pick(
            CURRENCIES.filter((currency) => currency !== beforeCurrency),
            rand,
          )
        : beforeCurrency

    items[i] = {
      userId: 1,
      beforeCurrency,
      afterCurrency,
      transactionTime: new Date(BASE_TIME_MS - i * INTERVAL_MS).toISOString(),
      data,
    }
  }

  return items
}
