package dev.be.coupon.infra.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AppliedUserRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = "applied_user:";

    public AppliedUserRepository(final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return true = 최초 발급 요청 (Redis에 성공적으로 추가됨), false = 이미 발급 요청 이력 있음
     */
    public boolean add(final UUID couponId, final UUID userId) {
        String key = KEY_PREFIX + couponId;
        Long result = redisTemplate
                .opsForSet()
                .add(key, userId.toString());
        return result != null && result > 0;
    }

    public void remove(final UUID couponId, final UUID userId) {
        String key = KEY_PREFIX + couponId;
        redisTemplate.opsForSet().remove(key, userId.toString());
    }
}
