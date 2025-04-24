import { authClient, publicClient } from './index'

// 로그인
export const login = (email, password) => {
    return publicClient.post('/login', { email, password })
}

// 로그아웃
export const logout = () => {
    return authClient.post('/logout')
}

// 회원가입
export const signup = (data) => {
    return publicClient.post('/api/v1/user/signup', data)
}

// 내 정보 조회
export const getMyInfo = () => {
    return authClient.get('/api/v1/user')
}

// 내 정보 수정
export const updateMyInfo = (data) => {
    return authClient.put('/api/v1/user', data)
}

// 회원 탈퇴
export const deleteMyAccount = () => {
    return authClient.delete('/api/v1/user')
}
