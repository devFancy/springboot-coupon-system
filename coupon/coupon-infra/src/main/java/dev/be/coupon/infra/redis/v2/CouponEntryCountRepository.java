package dev.be.coupon.infra.redis.v2;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// 입장 통제 게이트
@Repository
public class CouponEntryCountRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public static final String KEY_PREFIX = "coupon:v2:entry";

    public CouponEntryCountRepository(@Qualifier("couponV2RedisTemplate") final RedisTemplate<String, String> redisTemplate) {
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
