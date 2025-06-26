package dev.be.coupon.infra.redis.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson Distributed Lock annotation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 이름 (SpEL 표현식 사용 가능)
     */
    String key();

    /**
     * 락의 시간 단위 - waitTime 및 leaseTime 에 적용될 시간 단위입니다.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락 획득을 시도하는 최대 대기 시간입니다.
     * 이 시간 동안 락을 획득하지 못하면 실패로 간주합니다. (기본값: 5초)
     */
    long waitTime() default 5L;

    /**
     * 락을 성공적으로 획득한 후, 해당 락을 점유하는 시간(임대 시간)입니다. (기본값: 30초)
     * 이 시간이 지나면 락은 자동으로 해제될 수 있습니다.
     * Redisson 의 Watchdog 은 락을 점유한 스레드가 활성 상태인 동안 이 시간을 자동으로 연장하려고 시도합니다.
     * 주의: 이 시간이 실제 로직 수행 시간보다 너무 짧고 Watchdog 연장이 실패하면, 로직 실행 중 락이 해제될 위험이 있습니다.
     */
    long leaseTime() default 30L;
}
