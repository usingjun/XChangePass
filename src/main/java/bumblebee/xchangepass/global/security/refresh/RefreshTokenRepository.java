package bumblebee.xchangepass.global.security.refresh;

import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final RedisTemplate<String, Object> jsonRedisTemplate;

    private static final long REFRESH_TOKEN_TTL = 24 * 60 * 60; // 24시간 (초 단위)

    /**
     * Refresh Token 저장 (24시간 만료)
     */
    public void saveRefreshToken(String refreshToken, Long userId) {
        jsonRedisTemplate.opsForValue().set(refreshToken, userId, REFRESH_TOKEN_TTL, TimeUnit.SECONDS);
    }

    /**
     * Refresh Token으로 사용자 ID 조회
     */
    public Long getUserIdFromRefreshToken(String refreshToken) {
        Object userId = jsonRedisTemplate.opsForValue().get(refreshToken);
        if (userId == null) {
            throw ErrorCode.REFRESH_TOKEN_NOT_EXIST.commonException();
        }
        return Long.parseLong(userId.toString());
    }

    /**
     * Refresh Token 삭제
     */
    public void deleteRefreshToken(String refreshToken) {
        jsonRedisTemplate.delete(refreshToken);
    }

    /**
     * 특정 사용자의 모든 Refresh Token 삭제 (로그인 시 호출)
     */
    public void deleteUserRefreshTokens(Long userId) {
        Set<String> keys = jsonRedisTemplate.keys("*");
        if (keys != null) {
            for (String key : keys) {
                Object storedUserId = jsonRedisTemplate.opsForValue().get(key);
                if (storedUserId != null && storedUserId.toString().equals(String.valueOf(userId))) {
                    jsonRedisTemplate.delete(key);
                }
            }
        }
    }
}
