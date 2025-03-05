package bumblebee.xchangepass.global.scheduler;

import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@EnableAsync
public class UserCleanupScheduler {

    private final UserRepository userRepository;

    @Setter
    private Clock clock = Clock.systemUTC(); // 기본 시스템 시간을 사용


    /*
    삭제 요청에 오류 발생 시 해결 방법
    - 트랜잭션 작동 X -> Propagation.REQUIRES_NEW 사용
    - 로그 발생
    - slack 및 알림 발생

    또 다른 문제점
    삭제가 다른 쓰레드에서 실행된다면 동시에 삭제 복구 요청이 온다면?
     */
    @Async
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void deleteUser(){
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now(clock).minusDays(30);
            System.out.println("[DEBUG] deleteUser() 실행! 현재 스레드: " + Thread.currentThread().getName());
            userRepository.deleteOldUsers(thirtyDaysAgo);
        }catch (Exception e){
            throw ErrorCode.USER_NOT_DELETE.commonException();
        }
    }
}
