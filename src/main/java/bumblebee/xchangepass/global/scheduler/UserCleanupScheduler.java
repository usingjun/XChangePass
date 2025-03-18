package bumblebee.xchangepass.global.scheduler;

import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.user.service.UserService;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@EnableAsync
public class UserCleanupScheduler {

    private final UserService userService;

    @Setter
    private Clock clock = Clock.systemUTC(); // 기본 시스템 시간을 사용


    /**
     * Hard Delete 스케쥴러
     */
    @Async
    @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시에 실행
    public void deleteUser() {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now(clock).minusDays(30);

            userService.deleteUserBatch(thirtyDaysAgo);
        } catch (Exception e) {
            throw ErrorCode.USER_NOT_DELETE.commonException();
        }
    }

}
