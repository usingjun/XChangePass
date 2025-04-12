import axios from './axios'

// 환율 정보 조회 API
export const fetchExchangeRate = (baseCurrency) => {
    return axios.get(`http://localhost:8080/api/exchange-rate/${baseCurrency}`)
}

export const fetchExchangeRateByCountry = (baseCurrency, targetCurrency) => {
    return axios.get(`http://localhost:8080/api/exchange-rate/rate?baseCurrency=${baseCurrency}&targetCurrency=${targetCurrency}`)
}