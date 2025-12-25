package dev.be.coupon.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CouponRedisDuplicateValidate {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "coupon:participation_set:";

    public CouponRedisDuplicateValidate(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean isFirstUser(final UUID couponId, final UUID userId) {
        String key = getKey(couponId);
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return (result != null && result == 1L);
    }

    public void remove(final UUID couponId, final UUID userId) {
        String key = getKey(couponId);
        redisTemplate.opsForSet().remove(key, userId.toString());
    }

    private String getKey(final UUID couponId) {
        return KEY_PREFIX + couponId.toString();
    }
}
