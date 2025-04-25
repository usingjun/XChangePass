// src/api/index.js
import axios from 'axios'

const BASE_URL = 'http://localhost:8080'
const commonConfig = {
    baseURL: BASE_URL,
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' },
}

// 인증 불필요 요청 (인터셉터 없음)
export const publicClient = axios.create(commonConfig)

// 인증 필요 요청 (인터셉터만 추가)
export const authClient = axios.create(commonConfig)

// authClient에만 인터셉터 달기
authClient.interceptors.response.use(
    res => res,
    async err => {
        const { response, config } = err
        const originalReq = config
        if (response?.status === 401 && !originalReq._retry) {
            originalReq._retry = true
            try {
                await publicClient.post('/token-refresh')
                return authClient(originalReq)
            } catch {
                alert('세션 만료. 다시 로그인해주세요.')
                window.location.href = '/user-login'
            }
        }
        return Promise.reject(err)
    }
)
