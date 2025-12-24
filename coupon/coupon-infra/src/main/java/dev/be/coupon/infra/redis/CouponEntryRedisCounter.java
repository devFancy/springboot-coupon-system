package dev.be.coupon.infra.redis;

import dev.be.coupon.infra.exception.CouponInfraException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Component
public class CouponEntryRedisCounter {

    private final RedisTemplate<String, String> redisTemplate;

    public static final String KEY_PREFIX = "coupon:entry_count";

    public CouponEntryRedisCounter(final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long increment(final UUID couponId) {
        final String key = KEY_PREFIX + couponId;
        final Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new CouponInfraException("Redis 카운팅이 실패했습니다.");
        }
        if (count == 1) {
            redisTemplate.expire(key, 3, TimeUnit.DAYS);
        }
        return count;
    }
}
