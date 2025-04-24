
import { authApi } from '@/api/axios'

// 환율 정보 조회 API
export const fetchExchangeRate = (baseCurrency) => {
    return authApi.get(`http://localhost:8080/api/exchange-rate/${baseCurrency}`)
}

export const fetchExchangeRateByCountry = (baseCurrency, targetCurrency) => {
    return authApi.get(`http://localhost:8080/api/exchange-rate/rate?baseCurrency=${baseCurrency}&targetCurrency=${targetCurrency}`)
}

// 거래 생성 API
export const createExchangeTransaction = (data) => {
    return authApi.post('http://localhost:8080/api/exchange/create', data)
}

// 거래 실행 API
export const executeExchangeTransaction = (transactionId) => {
    return authApi.post(`http://localhost:8080/api/exchange/execute?transactionId=${transactionId}`)
}