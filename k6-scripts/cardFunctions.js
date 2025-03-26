import http from 'k6/http';
import { check, sleep } from 'k6';

// 실물 카드 발급 함수
export function generatePhysicalCard(BASE_URL, authParams) {
    let generateCardRes = http.post(`${BASE_URL}/api/v1/card/physical`, null, authParams, {
        tags: { method: 'POST', endpoint: 'generatePhysicalCard' },
    });

    check(generateCardRes, {
        'Card Generation Success': (r) => r.status === 201,
    });

    if (generateCardRes.status !== 201) {
        return null;
    }

    return generateCardRes;
}

// 카드 상태 변경 함수
export function changeCardStatus(BASE_URL, authParams, cardType, status) {
    let changeCardStatusPayload = JSON.stringify({
        cardType: cardType,
        status: status
    });

    let changeCardStatusRes = http.put(`${BASE_URL}/api/v1/card/status`, changeCardStatusPayload, authParams, {
        tags: { method: 'PUT', endpoint: 'changeCardStatus' },
    });

    check(changeCardStatusRes, {
        'Card Status Change Success': (r) => r.status === 204,
    });

    if (changeCardStatusRes.status !== 204) {
        return null;
    }

    return changeCardStatusRes;
}

// 카드 목록 조회 함수
export function getCardList(BASE_URL, authParams) {
    let cardListRes = http.get(`${BASE_URL}/api/v1/card`, authParams, {
        tags: {method: 'GET', endpoint: 'getCardList'},
    });

    check(cardListRes, {
        'Card List Fetch Success': (r) => r.status === 200,
    });

    if (cardListRes.status !== 200) {
        return null;
    }

    let cards = JSON.parse(cardListRes.body);

    if (cards.length === 0) {
        return null;
    }

    // 첫 번째 카드의 cardId 추출
    let firstCard = cards[0];
    let cardId = firstCard.cardId;

    if (!cardId) {
        return null;
    }

    return {
        cardId: cardId
    };
}

// 카드 상세 정보 조회 함수
export function getCardDetail(BASE_URL, authParams, cardId) {
    let cardDetailRes = http.get(`${BASE_URL}/api/v1/card/${cardId}`, authParams, {
        tags: { method: 'GET', endpoint: 'getCardDetail' },
    });

    check(cardDetailRes, {
        'Card Detail Fetch Success': (r) => r.status === 200,
    });

    if (cardDetailRes.status !== 200) {
        return null;
    }

    return cardDetailRes;
}

// 카드 상세 정보 조회 100번 반복 함수
export function getCardDetailRepeated(BASE_URL, authParams, cardId, repeatCount) {
    for (let i = 0; i < repeatCount; i++) {
        let cardDetailRes = http.get(`${BASE_URL}/api/v1/card/${cardId}`, authParams, {
            tags: { method: 'GET', endpoint: 'getCardDetail' },
        });

        check(cardDetailRes, {
            'Card Detail Fetch Success': (r) => r.status === 200,
        });

        if (cardDetailRes.status === 200) {
            console.error(`[${i + 1}/${repeatCount}] 카드 상세 정보 조회 성공`);
        } else {
            console.error(`[${i + 1}/${repeatCount}] 카드 상세 정보 조회 실패`);
        }

        sleep(0.1);
    }
}