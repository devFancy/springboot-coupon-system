package dev.be.coupon.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.infra.exception.CouponInfraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class CouponRedisCache {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String COUPON_KEY_PREFIX = "coupon:meta";
    private final Logger log = LoggerFactory.getLogger(CouponRedisCache.class);

    public CouponRedisCache(final RedisTemplate<String, String> redisTemplate, final ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(final Coupon coupon) {
        String key = COUPON_KEY_PREFIX + coupon.getId();
        try {
            String jsonValue = objectMapper.writeValueAsString(coupon);
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofHours(12));
        } catch (JsonProcessingException e) {
            log.error("[COUPON_REDIS_SERIALIZE_ERROR] 쿠폰 정보를 직렬화하는데 실패했습니다. couponId={}, name={}", coupon.getId(), coupon.getCouponName(), e);
            throw new CouponInfraException("쿠폰 정보를 직렬화하는데 실패했습니다.");
        }
    }

    // NOTE:쿠폰 ID를 사용하여 Redis에서 쿠폰 정보를 조회합니다.
    public Optional<Coupon> getCouponById(final UUID couponId) {
        String key = COUPON_KEY_PREFIX + couponId;
        String jsonValue = redisTemplate.opsForValue().get(key);

        if (jsonValue == null || jsonValue.isEmpty()) {
            return Optional.empty();
        }

        try {
            Coupon coupon = objectMapper.readValue(jsonValue, Coupon.class);
            return Optional.of(coupon);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize coupon from JSON: {}", jsonValue, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }


    public Integer getTotalQuantityById(final UUID couponId) {
        return getCouponById(couponId).map(Coupon::getTotalQuantity).orElse(null);
    }
}
