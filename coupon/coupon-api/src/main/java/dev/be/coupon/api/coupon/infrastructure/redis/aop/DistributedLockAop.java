package dev.be.coupon.api.coupon.infrastructure.redis.aop;

import dev.be.coupon.api.coupon.infrastructure.redis.exception.DistributedLockNotAcquiredException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 분산락 처리를 위한 AOP 클래스 (@DistributedLock 어노테이션 기반)
 */
@Aspect
@Component
public class DistributedLockAop {
    private static final Logger log = LoggerFactory.getLogger(DistributedLockAop.class);
    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopMethodExecutor aopMethodExecutor;

    public DistributedLockAop(final RedissonClient redissonClient, final AopMethodExecutor aopMethodExecutor) {
        this.redissonClient = redissonClient;
        this.aopMethodExecutor = aopMethodExecutor;
    }

    @Around("@annotation(distributedLockAnnotation)")
    public Object lock(final ProceedingJoinPoint joinPoint, final DistributedLock distributedLockAnnotation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String spELKeyExpression = distributedLockAnnotation.key();

        String dynamicKeyPart;
        try {
            Object spELResult = CustomSpringELParser.getDynamicValue(
                    signature.getParameterNames(), joinPoint.getArgs(), spELKeyExpression);

            // 평가된 값을 기반으로 실제 락 키 부분 생성
            if (spELResult == null || !StringUtils.hasText(spELResult.toString())) {
                dynamicKeyPart = method.getName(); // SpEL 결과가 부적절하면 메서드 이름 사용
            } else {
                dynamicKeyPart = spELResult.toString(); // 유효한 SpEL 결과 사용
            }
        } catch (Exception e) {
            dynamicKeyPart = method.getName();
            log.warn("Failed to evaluate SpEL for lock key, using method name as fallback. SpEL: '{}', Method: '{}', Error: {}",
                    spELKeyExpression, dynamicKeyPart, e.getMessage());
        }

        String lockName = REDISSON_LOCK_PREFIX + dynamicKeyPart;
        // (1) 락의 이름으로 RLock 인스턴스를 가져온다.
        RLock rLock = redissonClient.getLock(lockName);

        long waitTime = distributedLockAnnotation.waitTime();
        long leaseTime = distributedLockAnnotation.leaseTime();

        log.debug("Attempting to acquire lock: Key='{}', WaitTime={}s, LeaseTime={}s", lockName, waitTime, leaseTime);

        boolean acquired = false;
        try {
            // (2) 정의된 waitTime 까지 획득을 시도한다, 정의된 leaseTime 이 지나면 잠금을 해제한다.
            acquired = rLock.tryLock(waitTime, leaseTime, distributedLockAnnotation.timeUnit());

            if (!acquired) {
                log.warn("Failed to acquire lock: Key='{}'", lockName);
                throw new DistributedLockNotAcquiredException(lockName);
            }

            log.info("Lock acquired: Key='{}'", lockName);
            // (3) DistributedLock 어노테이션이 선언된 메서드를 별도의 트랜잭션으로 실행한다.
            return aopMethodExecutor.proceed(joinPoint);

        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for lock: Key='{}'", lockName, e);
            Thread.currentThread().interrupt();
            throw new DistributedLockNotAcquiredException(lockName);
        } finally {
            if (acquired) {
                try {
                    // (4) 종료 시 무조건 락을 해제한다.
                    rLock.unlock();
                    log.info("Lock released: Key='{}'", lockName);
                } catch (Exception e) {
                    log.warn("Exception during lock release (might be normal if lease expired). Key='{}', Method: '{}', ErrorMessage='{}'",
                            lockName, method.getName(), e.getMessage());
                }
            }
        }
    }
}
