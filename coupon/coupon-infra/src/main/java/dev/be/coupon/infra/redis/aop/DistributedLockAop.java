package dev.be.coupon.infra.redis.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
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

    public DistributedLockAop(final RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
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
            log.warn("락 키 SpEL 평가 실패, 메서드 이름을 대체 키로 사용합니다. SpEL: '{}', 메서드: '{}', 오류: {}",
                    spELKeyExpression, dynamicKeyPart, e.getMessage(), e);
        }

        String lockName = REDISSON_LOCK_PREFIX + dynamicKeyPart;
        // (1) 락의 이름으로 RLock 인스턴스를 가져온다.
        RLock rLock = redissonClient.getLock(lockName);

        long waitTime = distributedLockAnnotation.waitTime();
        long leaseTime = distributedLockAnnotation.leaseTime();

        log.debug("락 획득 시도: 키='{}', 대기시간={}s, 임대시간={}s", lockName, waitTime, leaseTime);

        boolean acquired = false;
        try {
            // (2) 정의된 waitTime 까지 획득을 시도한다, 정의된 leaseTime 이 지나면 잠금을 해제한다.
            acquired = rLock.tryLock(waitTime, leaseTime, distributedLockAnnotation.timeUnit());

            if (!acquired) {
                log.warn("락 획득 실패: 키='{}'", lockName);
                throw new RuntimeException("락 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }

            StopWatch stopWatch = new StopWatch();
            try {
                stopWatch.start();
                log.info("락 획득 성공 및 비즈니스 로직 시작: 키='{}'", lockName);
                // (3) @DistributedLock 어노테이션이 선언된 원본 메서드 실행.
                return joinPoint.proceed();
            } finally {
                stopWatch.stop();
                log.info("비즈니스 로직 종료: 키='{}', 실행시간={}ms",
                        lockName,
                        stopWatch.getTotalTimeMillis());
            }
        } catch (InterruptedException e) {
            log.error("락 대기 중 인터럽트 발생: 키='{}'", lockName, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("일시적으로 요청이 많아 락을 획득할 수 없습니다. 잠시 후 다시 시도해주세요.");
        } finally {
            if (acquired && rLock.isHeldByCurrentThread()) {
                try {
                    // (4) 종료 시 무조건 락을 해제한다.
                    rLock.unlock();
                    log.info("락 해제 성공: 키='{}'", lockName);
                } catch (IllegalMonitorStateException imse) {
                    log.warn("락 해제 시도 시 이미 해제되었거나 현재 스레드가 락을 보유하고 있지 않음 (IllegalMonitorStateException). 키='{}', 메서드: '{}', 오류 메시지='{}'",
                            lockName, method.getName(), imse.getMessage());
                } catch (Exception e) {
                    log.warn("락 해제 중 예외 발생. 키='{}', 메서드: '{}', 오류 메시지='{}'",
                            lockName, method.getName(), e.getMessage());
                }
            }
        }
    }
}
