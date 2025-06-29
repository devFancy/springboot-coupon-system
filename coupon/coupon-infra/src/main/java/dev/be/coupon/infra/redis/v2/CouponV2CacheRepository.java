package dev.be.coupon.infra.redis.v2;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.vo.CouponName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository("CouponV2CacheRepository")
public class CouponV2CacheRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String COUPON_KEY_PREFIX = "coupon:v2:meta";

    public CouponV2CacheRepository(@Qualifier("couponV2RedisTemplate") final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Coupon 객체의 필드를 Redis Hash 자료구조에 저장합니다.
     */
    public void save(final Coupon coupon) {
        String key = COUPON_KEY_PREFIX + coupon.getId();
        Map<String, String> fields = Map.of(
                "id", coupon.getId().toString(),
                "name", coupon.getCouponName().getName(),
                "totalQuantity", String.valueOf(coupon.getTotalQuantity()),
                "couponStatus", coupon.getCouponStatus().name(),
                "couponType", coupon.getCouponType().name(),
                "validFrom", coupon.getValidFrom().toString(),
                "validUntil", coupon.getValidUntil().toString()
        );
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Duration.ofHours(12));
    }

    /**
     * Redis Hash에서 모든 필드를 가져와 Coupon 객체를 복원합니다.
     */
    public Optional<Coupon> findById(final UUID couponId) {
        String key = COUPON_KEY_PREFIX + couponId;

        // HGETALL 명령어에 해당하는 entries()로 Hash의 모든 필드를 Map으로 가져옵니다.
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);

        // 캐시된 데이터가 없으면 캐시 미스이므로 Optional.empty() 반환
        if (fields.isEmpty() || fields.get("id") == null) {
            return Optional.empty();
        }
        Coupon coupon = new Coupon(
                UUID.fromString(String.valueOf(fields.get("id"))),
                new CouponName(String.valueOf(fields.get("name"))),
                CouponType.valueOf(String.valueOf(fields.get("couponType"))),
                Integer.parseInt(String.valueOf(fields.get("totalQuantity"))),
                CouponStatus.valueOf(String.valueOf(fields.get("couponStatus"))),
                LocalDateTime.parse(String.valueOf(fields.get("validFrom"))),
                LocalDateTime.parse(String.valueOf(fields.get("validUntil")))
        );

        return Optional.of(coupon);
    }

    /**
     * 쿠폰의 총 수량만 Redis Hash에서 효율적으로 조회합니다. (HGET)
     */
    public Integer getTotalQuantity(final UUID couponId) {
        String key = COUPON_KEY_PREFIX + couponId;
        Object rawValue = redisTemplate.opsForHash().get(key, "totalQuantity");

        if (rawValue == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(rawValue));
    }
}
