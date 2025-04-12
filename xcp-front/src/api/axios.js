import axios from 'axios'

const axiosInstance = axios.create({
    baseURL: '/api',  // vite proxy랑 연결됨
    timeout: 200000,
    headers: {
        'Content-Type': 'application/json',
    },
})

export default axiosInstance