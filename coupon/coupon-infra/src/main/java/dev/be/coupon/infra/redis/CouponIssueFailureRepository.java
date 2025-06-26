package dev.be.coupon.infra.redis;

import dev.be.coupon.domain.coupon.CouponIssueStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

@Repository
public class CouponIssueFailureRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public CouponIssueFailureRepository(final RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final Duration TTL = Duration.ofMinutes(60);

    private String generateKey(UUID couponId, UUID userId) {
        return "failure:coupon:" + couponId + ":user:" + userId;
    }

    public void record(final UUID couponId, final UUID userId, final CouponIssueStatus reason) {
        if (reason == null)
            return;
        redisTemplate.opsForValue().set(generateKey(couponId, userId), reason.name(), TTL);
    }

    public CouponIssueStatus getFailureReason(final UUID couponId, final UUID userId) {
        String reasonStr = redisTemplate.opsForValue().get(generateKey(couponId, userId));
        if (reasonStr == null) {
            return null;
        }
        try {
            return CouponIssueStatus.valueOf(reasonStr);
        } catch (IllegalArgumentException e) {
            return CouponIssueStatus.FAILED_UNKNOWN;
        }
    }
}
