package dev.be.coupon.infra.redis.v2;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

@Repository
public class CouponSoldOutCacheRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "coupon:v2:sold_out:";

    public CouponSoldOutCacheRepository(@Qualifier("couponV2RedisTemplate") final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 쿠폰이 매진되었는지 확인합니다.
     * @return true: 매진됨, false: 아직 매진되지 않음(또는 키가 없음)
     */
    public boolean isSoldOut(final UUID couponId) {
        String key = KEY_PREFIX + couponId;
        return "true".equals(redisTemplate.opsForValue().get(key));
    }

    /**
     * 쿠폰의 매진 상태를 기록합니다.
     */
    public void setSoldOut(final UUID couponId) {
        String key = KEY_PREFIX + couponId;
        redisTemplate.opsForValue().set(key, "true", Duration.ofHours(1));
    }
}
