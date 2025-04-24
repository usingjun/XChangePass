import axios from 'axios'

const baseURL = 'http://localhost:8080/'

// 로그인 후 쿠키 사용하는 API
export const authApi = axios.create({
    baseURL,
    withCredentials: true,
})

// 공개 API (필요시)
export const publicApi = axios.create({
    baseURL,
})

export default axios.create({
    baseURL: '/api',
    timeout: 20000,
    headers: {
        'Content-Type': 'application/json',
    },
})