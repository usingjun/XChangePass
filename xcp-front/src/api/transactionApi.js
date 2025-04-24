import { authClient } from './index'

// 거래 내역 목록 조회
export const getCardTransactions = (lastTransactionId, size = 10) => {
    return authClient.get('/api/v1/card/transactions', {
        params: { lastTransactionId, size }
    })
}

// 거래 상세 정보 조회
export const getCardTransactionDetail = (transactionId) => {
    return authClient.get(`/api/v1/card/transactions/${transactionId}`)
}
