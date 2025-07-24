package dev.be.coupon.infra.redis.v1;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class CouponCountRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public CouponCountRedisRepository(final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long increment(final String key) {
        return redisTemplate
                .opsForValue()
                .increment(key); // INCR
    }

    public Long decrement(final String key) {
        return redisTemplate
                .opsForValue()
                .decrement(key); // DECR
    }

    public boolean tryLock(final String lockKey, final long timeoutSeconds) {
        return Boolean.TRUE.equals(redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(timeoutSeconds)));
    }

    public void releaseLock(final String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
