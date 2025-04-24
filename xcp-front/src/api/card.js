import { authClient } from './index'

// 실물 카드 발급
export const generatePhysicalCard = () => {
    return authClient.post('/api/v1/card/physical')
}

// 카드 상태 변경 (활성화/비활성화 등)
export const changeCardStatus = (data) => {
    return authClient.put('/api/v1/card/status', data)
}

// 보유 카드 목록 조회
export const getCardList = () => {
    return authClient.get('/api/v1/card')
}

// 카드 상세 정보 조회
export const getCardDetail = (cardId) => {
    return authClient.get(`/api/v1/card/${cardId}`)
}
