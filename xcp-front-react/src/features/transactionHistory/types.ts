// 백엔드 대응 소스:
// - bumblebee.xchangepass.domain.transaction.dto.response.{TransactionResponse,TransactionPageResponse,WalletTransactionDto,CardTransactionDto,ExchangeTransactionDto}
// - bumblebee.xchangepass.domain.transaction.entity.{TransactionType,TransactionDirection}
// - bumblebee.xchangepass.domain.wallet.transaction.entity.WalletTransactionType
// - bumblebee.xchangepass.domain.cardTransaction.entity.CardTransactionType
//
// data 필드는 백엔드에서 Jackson @JsonTypeInfo(use = Id.CLASS, property = "@class")로 직렬화되지만,
// "@class"(Java 클래스 풀네임)는 파싱하지 않는다. 각 서브 DTO에 공통으로 있는
// data.transactionType 을 discriminant로 써서 TS discriminated union을 구성한다.

export type TransactionType = 'WALLET' | 'CARD' | 'EXCHANGE'

export type TransactionDirection = 'ALL' | 'SENT' | 'RECEIVED'

export type WalletTransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER'

export type CardTransactionType = 'PAYMENT' | 'DEPOSIT' | 'REFUND'

export interface WalletTransactionDto {
  transactionType: 'WALLET'
  counterpartyUserId: number
  // BigDecimal 직렬화 값. 정밀도 손실을 피하기 위해 number로 파싱하지 않고 string으로 취급한다.
  amount: string
  direction: TransactionDirection
  walletType: WalletTransactionType
}

export interface CardTransactionDto {
  transactionType: 'CARD'
  merchant: string
  amount: string
  balanceAfter: string
  cardType: CardTransactionType
}

export interface ExchangeTransactionDto {
  transactionType: 'EXCHANGE'
  beforeAmount: string
  afterAmount: string
  rate: string
}

export type TransactionDataDto = WalletTransactionDto | CardTransactionDto | ExchangeTransactionDto

export interface TransactionResponse {
  userId: number
  // java.util.Currency 직렬화 값 (ISO 통화 코드, 예: "USD")
  beforeCurrency: string
  afterCurrency: string
  // ISO datetime 문자열
  transactionTime: string
  data: TransactionDataDto
}

export interface TransactionPageResponse {
  items: TransactionResponse[]
  nextCursor: string | null
  hasNext: boolean
}
