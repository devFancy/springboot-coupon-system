package dev.be.coupon.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis Set 을 사용하여 쿠폰 이벤트 참여자를 관리하는 컴포넌트입니다.
 * 사용자의 중복 참여 여부를 확인하는 역할을 합니다.
 */
@Component
public class CouponRedisDuplicateValidate {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "coupon:participation_set:";

    public CouponRedisDuplicateValidate(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 이벤트 참여 Set에 사용자를 추가합니다. (SADD)
     */
    public Boolean isFirstUser(final UUID couponId, final UUID userId) {
        String key = getKey(couponId);
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return (result != null && result == 1L);
    }

    /**
     * 이벤트 참여 Set에서 특정 사용자를 제거합니다. (SREM)
     * 선착순 마감 시 보상 로직으로 사용됩니다.
     */
    public void remove(final UUID couponId, final UUID userId) {
        String key = getKey(couponId);
        redisTemplate.opsForSet().remove(key, userId.toString());
    }

    private String getKey(final UUID couponId) {
        return KEY_PREFIX + couponId.toString();
    }
}
