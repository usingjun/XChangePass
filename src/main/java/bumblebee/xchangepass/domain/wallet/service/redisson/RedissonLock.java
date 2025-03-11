package bumblebee.xchangepass.domain.wallet.service.redisson;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RedissonLock {

    private final RedissonClient redissonClient;

    /**
     * 락을 시도하고, 성공하면 실행하는 메서드
     * @param lockName 락 이름
     * @param waitTime 락을 기다리는 시간
     * @param leaseTime 락 유지 시간
     * @param task 실행할 코드 (람다식)
     */
    public <T> T tryLock(String lockName, long waitTime, long leaseTime, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                try {
                    return task.get(); // 반환값 지원
                } finally {
                    unlock(lock);
                }
            } else {
                throw new RuntimeException("🚨 락 획득 실패: " + lockName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("🚨 락 시도 중 인터럽트 발생: " + lockName, e);
        }
    }

    public void tryLockVoid(String lockName, long waitTime, long leaseTime, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                try {
                    task.run(); // 실행할 로직 수행
                } finally {
                    unlock(lock);
                }
            } else {
                throw new RuntimeException("🚨 락 획득 실패: " + lockName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("🚨 락 시도 중 인터럽트 발생: " + lockName, e);
        }
    }


    /**
     * 강제적으로 락을 실행하는 메서드
     * @param lockName 락 이름
     * @param leaseTime 락 유지 시간
     * @param task 실행할 코드 (람다식)
     */
    public void lock(String lockName, long leaseTime, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(leaseTime, TimeUnit.SECONDS);
        try {
            task.run();
        } finally {
            unlock(lock);
        }
    }

    /**
     * 락 해제
     * @param lock 락 객체
     */
    private void unlock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }
}
