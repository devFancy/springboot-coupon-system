package dev.be.coupon.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Redis의 Sorted Set을 사용하여 쿠폰 발급 대기열을 관리하는 컴포넌트입니다.
 * 선착순 로직에서 사용자의 참여 순서를 기록하고 관리하는 역할을 합니다.
 */
@Component
public class CouponRedisWaitingQueue {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "coupon:wait_queue:";

    public CouponRedisWaitingQueue(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 대기열에 사용자를 추가 (ZADD의 NX 옵션으로 중복 방지)
     * score로 현재 시간을 사용하여 선착순을 보장합니다.
     */
    public Boolean add(final UUID couponId, final UUID userId) {
        String key = getKey(couponId);
        return redisTemplate.opsForZSet().addIfAbsent(key, userId.toString(), (double) System.nanoTime());
    }

    /**
     * 특정 쿠폰 대기열의 현재 크기를 반환합니다. (ZCARD)
     */
    public Long getQueueSize(final UUID couponId) {
        String key = getKey(couponId);
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * 처리된 사용자를 대기열에서 제거합니다. (ZREM)
     */
    public void remove(UUID couponId, Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String key = getKey(couponId);
        redisTemplate.opsForZSet().remove(key, (Object[]) userIds.toArray(new String[0]));
    }

    private String getKey(final UUID couponId) {
        return KEY_PREFIX + couponId.toString();
    }
}
