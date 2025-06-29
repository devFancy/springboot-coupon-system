package dev.be.coupon.infra.redis.v2;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

// 처리 대기실
@Repository
public class CouponWaitingQueueRepository {

    private final StringRedisTemplate redisTemplate;

    public CouponWaitingQueueRepository(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getKey(final UUID couponId) {
        return "coupon:wait_queue:" + couponId.toString();
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
     * 대기열에서 지정된 수만큼 사용자를 가져옵니다.  (ZRANGE)
     */
    public Set<String> getWaitingUsers(final UUID couponId, long count) {
        String key = getKey(couponId);
        return redisTemplate.opsForZSet().range(key, 0, count - 1);
    }

    /**
     * 처리된 사용자를 대기열에서 제거합니다. (ZREM)
     */
    public void remove(UUID couponId, Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String key = getKey(couponId);
        redisTemplate.opsForZSet().remove(key, userIds.toArray(new String[0]));
    }

    /**
     * 특정 쿠폰 대기열의 현재 크기를 반환합니다. (ZCARD)
     */
    public Long getQueueSize(final UUID couponId) {
        String key = getKey(couponId);
        return redisTemplate.opsForZSet().size(key);
    }
}
