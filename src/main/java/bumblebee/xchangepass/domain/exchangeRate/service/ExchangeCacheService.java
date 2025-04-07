//package bumblebee.xchangepass.domain.exchangeRate.service;
//
//import bumblebee.xchangepass.domain.exchangeRate.dto.response.ExchangeRateResponse;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class ExchangeCacheService {
//
//    private final RedisTemplate<String, ExchangeRateResponse> redisTemplate;
//
//    public ExchangeCacheService(@Qualifier("exchangeRedisTemplate") RedisTemplate<String, ExchangeRateResponse> redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }
//
//    // ✅ 카드 정보를 Redis에 저장 (30분 동안 유지)
//    public void saveCardInfo(String currency, ExchangeRateResponse exchangeRateResponse) {
//        redisTemplate.opsForValue().set(currency, exchangeRateResponse, 30, TimeUnit.MINUTES);
//    }
//
//    // ✅ Redis에서 카드 정보 가져오기
//    public ExchangeRateResponse getCardInfo(String currency) {
//        return redisTemplate.opsForValue().get(currency);
//    }
//
//}
//
//
