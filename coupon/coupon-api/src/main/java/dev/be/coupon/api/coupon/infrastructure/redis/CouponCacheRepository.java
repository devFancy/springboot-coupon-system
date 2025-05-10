package dev.be.coupon.api.coupon.infrastructure.redis;

import dev.be.coupon.api.coupon.domain.Coupon;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CouponCacheRepository {

    private final RedisTemplate<String, Coupon> redisTemplate;
    private static final String COUPON_KEY_PREFIX = "coupon:";

    public CouponCacheRepository(RedisTemplate<String, Coupon> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<Coupon> findById(final UUID couponId) {
        String key = COUPON_KEY_PREFIX + couponId;
        Coupon cached = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(cached);
    }

    public void save(Coupon coupon) {
        String key = COUPON_KEY_PREFIX + coupon.getId();
        // 해당 쿠폰 캐시의 TTL(Time-To-Live) 값
        redisTemplate.opsForValue().set(key, coupon, Duration.ofMinutes(10));
    }
}
