package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.global.Constants;
import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Redis INCR을 활용한 고유 닉네임 생성
     */
    public String generateUniqueNickname() {
        try {
            Long id = redisTemplate.opsForValue().increment(Constants.NICKNAME_KEY);
            return "User_" + (1000000000L + id);
        }catch (Exception e) {
            throw ErrorCode.REDIS_CONNECTION_ERROR.commonException();
        }
    }
}
