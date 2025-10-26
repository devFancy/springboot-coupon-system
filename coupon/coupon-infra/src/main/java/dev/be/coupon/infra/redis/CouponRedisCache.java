package dev.be.coupon.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.be.coupon.domain.coupon.Coupon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis를 사용하여 쿠폰 메타데이터를 캐싱하는 컴포넌트입니다.
 * DB 조회를 줄이기 위해 쿠폰 정보를 Redis에 저장하고 조회하는 역할을 담당합니다.
 */
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

    /**
     * Coupon 객체를 JSON 문자열로 직렬화하여 Redis에 저장합니다.
     */
    public void save(final Coupon coupon) {
        String key = COUPON_KEY_PREFIX + coupon.getId();
        try {
            String jsonValue = objectMapper.writeValueAsString(coupon);
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofHours(12));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize coupon to JSON: {}", coupon, e);
            throw new RuntimeException("쿠폰을 캐시에 저장하는 데 실패했습니다.", e);
        }
    }

    /**
     * 쿠폰 ID를 사용하여 Redis에서 쿠폰 정보를 조회합니다.
     */
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
