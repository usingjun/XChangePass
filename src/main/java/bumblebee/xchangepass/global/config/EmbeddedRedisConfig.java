package bumblebee.xchangepass.global.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "test")
public class EmbeddedRedisConfig {

    private final RedisServer redisServer;

    public EmbeddedRedisConfig() throws IOException {
        this.redisServer = new RedisServer(6379);  // 포트 번호 설정
        redisServer.start();
    }

    @Bean
    public RedisServer redisServer() {
        return redisServer;
    }

    /**
     * 애플리케이션 종료 시 Embedded Redis도 함께 종료.
     */
    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
