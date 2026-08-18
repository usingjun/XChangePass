import { columnSizingFeature, createColumnHelper, tableFeatures } from '@tanstack/react-table'
import type {
  CardTransactionType,
  TransactionDirection,
  TransactionResponse,
  WalletTransactionType,
} from '../types'

// status 필드는 응답에 없다(wallet/card 거래에 상태 개념이 없음 — docs/frontend-plan.md 2.4절).
// 대신 거래 유형(transactionType)과 서브타입(walletTransactionType/cardTransactionType/direction)을
// 셀 렌더러 분기의 기준으로 삼는다.

const TRANSACTION_TYPE_LABEL: Record<TransactionResponse['data']['transactionType'], string> = {
  WALLET: '지갑',
  CARD: '카드',
  EXCHANGE: '환전',
}

const WALLET_TYPE_LABEL: Record<WalletTransactionType, string> = {
  DEPOSIT: '충전',
  WITHDRAWAL: '출금',
  TRANSFER: '이체',
}

const WALLET_DIRECTION_LABEL: Record<TransactionDirection, string> = {
  ALL: '',
  SENT: '보냄',
  RECEIVED: '받음',
}

const CARD_TYPE_LABEL: Record<CardTransactionType, string> = {
  PAYMENT: '결제',
  DEPOSIT: '충전',
  REFUND: '환불',
}

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

function formatDateTime(iso: string): string {
  const parsed = new Date(iso)
  return Number.isNaN(parsed.getTime()) ? iso : dateTimeFormatter.format(parsed)
}

// amount 계열 필드는 BigDecimal 직렬화 문자열이다. Number로 변환하면 정밀도 손실
// 위험이 있으므로 문자열 그대로 자릿수 구분자만 삽입한다.
function formatAmount(raw: string): string {
  const negative = raw.startsWith('-')
  const unsigned = negative ? raw.slice(1) : raw
  const [integerPart, fractionPart] = unsigned.split('.')
  const withThousands = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const formatted = fractionPart ? `${withThousands}.${fractionPart}` : withThousands
  return negative ? `-${formatted}` : formatted
}

function formatCurrencyAmount(amount: string, currency: string): string {
  return `${formatAmount(amount)} ${currency}`
}

function renderDetail(row: TransactionResponse): string {
  const { data } = row
  switch (data.transactionType) {
    case 'WALLET':
      return `${WALLET_DIRECTION_LABEL[data.direction]} ${WALLET_TYPE_LABEL[data.walletType]}`.trim()
    case 'CARD':
      return `${CARD_TYPE_LABEL[data.cardType]} · ${data.merchant}`
    case 'EXCHANGE':
      return `${row.beforeCurrency} → ${row.afterCurrency} (환율 ${data.rate})`
  }
}

function renderAmount(row: TransactionResponse): string {
  const { data } = row
  switch (data.transactionType) {
    case 'WALLET':
      return formatCurrencyAmount(data.amount, row.afterCurrency)
    case 'CARD':
      return formatCurrencyAmount(data.amount, row.afterCurrency)
    case 'EXCHANGE':
      return `${formatCurrencyAmount(data.beforeAmount, row.beforeCurrency)} → ${formatCurrencyAmount(data.afterAmount, row.afterCurrency)}`
  }
}

// sorting/filtering/pagination 등은 여전히 등록하지 않는다 — row model 그대로 렌더링만 한다.
// columnSizingFeature만 예외로 등록한다: table-layout: fixed 벤치마크 모드(TransactionTable의
// fixedLayout prop)가 각 컬럼의 header.getSize()를 <colgroup>에 써야 해서 필요하다.
// TransactionTable이 useTable에 넘길 때도 이 features를 그대로 써야 컬럼 제네릭(TFeatures)이 일치한다.
export const features = tableFeatures({ columnSizingFeature })

const columnHelper = createColumnHelper<typeof features, TransactionResponse>()

export const transactionColumns = columnHelper.columns([
  columnHelper.accessor('transactionTime', {
    id: 'transactionTime',
    header: '거래일시',
    size: 180,
    cell: (info) => formatDateTime(info.getValue()),
  }),
  columnHelper.accessor((row) => row.data.transactionType, {
    id: 'transactionType',
    header: '유형',
    size: 90,
    cell: (info) => {
      const type = info.getValue()
      return (
        <span className={`transaction-badge transaction-badge--${type.toLowerCase()}`}>
          {TRANSACTION_TYPE_LABEL[type]}
        </span>
      )
    },
  }),
  columnHelper.accessor((row) => renderDetail(row), {
    id: 'detail',
    header: '내용',
    size: 280,
  }),
  columnHelper.accessor((row) => renderAmount(row), {
    id: 'amount',
    header: '금액',
    size: 220,
  }),
])
