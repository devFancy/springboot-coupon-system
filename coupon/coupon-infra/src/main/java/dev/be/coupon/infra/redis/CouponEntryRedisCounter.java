package dev.be.coupon.infra.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis의 INCR 명령어를 사용하여 쿠폰 이벤트의 참여자 수를 카운팅하는 컴포넌트입니다.
 */
@Component
public class CouponEntryRedisCounter {

    private final RedisTemplate<String, String> redisTemplate;

    public static final String KEY_PREFIX = "coupon:entry_count";

    public CouponEntryRedisCounter(final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 특정 쿠폰 이벤트의 참여자 수를 1 증가시키고, 현재까지의 총 참여자 수를 반환합니다.
     */
    public long increment(final UUID couponId) {
        String key = KEY_PREFIX + couponId;
        return redisTemplate.opsForValue().increment(key);
    }
}
