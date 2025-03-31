package bumblebee.xchangepass.global.config;

import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // JSON 직렬화를 위한 RedisTemplate
    @Bean(name = "jsonRedisTemplate")
    public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());

        // Value는 JSON 직렬화 (GenericJackson2JsonRedisSerializer 사용)
        ObjectMapper redisObjectMapper = new ObjectMapper();
        redisObjectMapper.findAndRegisterModules();
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        return template;
    }

    // ✅ DetailedCardInfoResponse 전용 RedisTemplate
    @Bean(name = "cardInfoRedisTemplate")
    public RedisTemplate<String, DetailedCardInfoResponse> cardInfoRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, DetailedCardInfoResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());

        return template;
    }

//    @Bean(name = "exchangeRedisTemplate")
//    public RedisTemplate<String, ExchangeRateResponse> cardInfoRedisTemplate2(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, ExchangeRateResponse> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(new JdkSerializationRedisSerializer());
//
//        return template;
//    }

}
