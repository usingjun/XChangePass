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

    private static final Long REFRESH_TOKEN_TTL = 24 * 60 * 60L; // 24시간 (초 단위)

    /**
     * Refresh Token 저장 (24시간 만료)
     */
    public void saveRefreshToken(String refreshToken, Long userId) {
        String key = "refresh_tokens:" + userId;
        // HashMap의 field로 refreshToken을 key로 저장하고, value는 "valid" 같은 임의의 값 사용
        jsonRedisTemplate.opsForHash().put(key, refreshToken, "valid");

        // 사용자별 TTL 설정 (최초 저장 시만 적용)
        jsonRedisTemplate.expire(key, REFRESH_TOKEN_TTL, TimeUnit.SECONDS);
    }

    /**
     * Refresh Token으로 사용자 ID 조회
     */
    public Long getUserIdFromRefreshToken(String refreshToken) {
        String userId = findUserIdByRefreshToken(refreshToken);
        if (userId == null) {
            throw ErrorCode.REFRESH_TOKEN_NOT_EXIST.commonException();
        }
        return Long.parseLong(userId);
    }

    /**
     * Refresh Token 삭제
     */
    public void deleteRefreshToken(String refreshToken) {
        // Refresh Token을 보유한 사용자 ID 찾기
        String userId = findUserIdByRefreshToken(refreshToken);

        if (userId == null) {
            throw ErrorCode.REFRESH_TOKEN_NOT_EXIST.commonException();
        }

        String key = "refresh_tokens:" + userId;
        jsonRedisTemplate.opsForHash().delete(key, refreshToken);
    }

    /**
     * 특정 사용자의 모든 Refresh Token 삭제 (로그인 시 호출)
     */
    public void deleteUserRefreshTokens(Long userId) {
        String key = "refresh_tokens:" + userId;
        jsonRedisTemplate.delete(key);
    }

    /**
     * Refresh Token으로 사용자 ID 찾기
     */
    private String findUserIdByRefreshToken(String refreshToken) {
        // 모든 사용자 ID에 대해 반복을 돌 필요 없이 특정 key 내에서 검색
        for (String key : jsonRedisTemplate.keys("refresh_tokens:*")) {
            if (Boolean.TRUE.equals(jsonRedisTemplate.opsForHash().hasKey(key, refreshToken))) {
                return key.split(":")[1]; // "refresh_tokens:userId"에서 userId 추출
            }
        }
        return null;
    }
}
