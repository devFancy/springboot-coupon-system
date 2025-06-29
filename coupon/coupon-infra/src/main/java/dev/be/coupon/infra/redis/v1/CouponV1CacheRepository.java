package dev.be.coupon.infra.redis.v1;

import dev.be.coupon.domain.coupon.Coupon;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository("CouponV1CacheRepository")
public class CouponV1CacheRepository {

    private final RedisTemplate<String, Coupon> redisTemplate;
    private static final String COUPON_KEY_PREFIX = "coupon:v1:meta";

    public CouponV1CacheRepository(final RedisTemplate<String, Coupon> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<Coupon> findById(final UUID couponId) {
        String key = COUPON_KEY_PREFIX + couponId;
        Coupon cached = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(cached);
    }

    public void save(final Coupon coupon) {
        String key = COUPON_KEY_PREFIX + coupon.getId();
        // 해당 쿠폰 캐시의 TTL(Time-To-Live) 값
        redisTemplate.opsForValue().set(key, coupon, Duration.ofMinutes(10));
    }
}
