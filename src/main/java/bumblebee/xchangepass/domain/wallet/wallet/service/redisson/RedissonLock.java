package bumblebee.xchangepass.domain.wallet.wallet.service.redisson;

import bumblebee.xchangepass.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
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
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                throw ErrorCode.LOCK_TIME_OUT.commonException();
            }
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorCode.THREAD_INTERRUPTED.commonException();
        } finally {
            if (acquired) {
                unlock(lock);
            }
        }
    }

    public void tryLockVoid(String lockName, long waitTime, long leaseTime, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                throw ErrorCode.LOCK_TIME_OUT.commonException();
            }
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorCode.THREAD_INTERRUPTED.commonException();
        } finally {
            if (acquired) {
                unlock(lock);
            }
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
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            log.error("⚠️ [Lock 해제 실패] Lock Name: {}", lock.getName(), e);
        }
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }
}
