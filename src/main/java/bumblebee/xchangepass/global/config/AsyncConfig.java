package bumblebee.xchangepass.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(30);  // 기본 스레드 수
        executor.setMaxPoolSize(32);  // 최대 스레드 수
        executor.setQueueCapacity(500);  // 작업 큐 크기
        executor.setThreadNamePrefix("AsyncThread-");  // 스레드 이름 설정
        executor.initialize();
        return executor;
    }
}