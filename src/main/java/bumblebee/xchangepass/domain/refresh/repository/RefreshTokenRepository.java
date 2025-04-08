package bumblebee.xchangepass.domain.refresh.repository;

import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

import static bumblebee.xchangepass.global.common.Constants.REFRESH_TOKEN_TTL;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final RedisTemplate<String, Object> jsonRedisTemplate;

    /**
     * Refresh Token 저장 (사용자당 하나만 유지, 기존 토큰 자동 삭제)
     */
    public void saveRefreshToken(String refreshToken, Long userId) {
        String key = "refresh_token:" + userId;
        jsonRedisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL, TimeUnit.SECONDS);
    }

    /**
     * Refresh Token으로 사용자 ID 조회
     */
    public Long getUserIdFromRefreshToken(String refreshToken) {
        // userId를 통해 직접 Refresh Token을 조회
        for (String key : jsonRedisTemplate.keys("refresh_token:*")) {
            String storedToken = (String) jsonRedisTemplate.opsForValue().get(key);
            if (refreshToken.equals(storedToken)) {
                return Long.parseLong(key.split(":")[1]); // "refresh_token:userId"에서 userId 추출
            }
        }
        throw ErrorCode.REFRESH_TOKEN_NOT_EXIST.commonException();
    }

    /**
     * 특정 사용자의 Refresh Token 삭제
     */
    public void deleteRefreshToken(Long userId) {
        jsonRedisTemplate.delete("refresh_token:" + userId);
    }

    /**
     * Refresh Token 검증 (저장된 것과 일치하는지 확인)
     */
    public boolean isValidRefreshToken(Long userId, String refreshToken) {
        String storedToken = (String) jsonRedisTemplate.opsForValue().get("refresh_token:" + userId);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    /**
     * 사용자 ID로 Refresh Token 조회
     */
    public String getRefreshToken(Long userId) {
        String key = "refresh_token:" + userId;
        return (String) jsonRedisTemplate.opsForValue().get(key);
    }
}
