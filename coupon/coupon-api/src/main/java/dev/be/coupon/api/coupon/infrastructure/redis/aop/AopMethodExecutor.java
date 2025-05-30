package dev.be.coupon.api.coupon.infrastructure.redis.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

@Component("aopMethodExecutor")
public class AopMethodExecutor {

    public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
