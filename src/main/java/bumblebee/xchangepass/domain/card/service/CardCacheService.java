package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class CardCacheService {

    private final RedisTemplate<String, DetailedCardInfoResponse> redisTemplate;

    public CardCacheService(@Qualifier("cardInfoRedisTemplate") RedisTemplate<String, DetailedCardInfoResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ✅ 카드 정보를 Redis에 저장 (30분 동안 유지)
    public void saveCardInfo(Long cardId, DetailedCardInfoResponse cardInfo) {
        redisTemplate.opsForValue().set(getCacheKey(cardId), cardInfo, 30, TimeUnit.MINUTES);
    }

    // ✅ Redis에서 카드 정보 가져오기
    public DetailedCardInfoResponse getCardInfo(Long cardId) {
        return redisTemplate.opsForValue().get(getCacheKey(cardId));
    }

    // ✅ 캐시 키 생성
    private String getCacheKey(Long cardId) {
        return "card:" + cardId;
    }
}


