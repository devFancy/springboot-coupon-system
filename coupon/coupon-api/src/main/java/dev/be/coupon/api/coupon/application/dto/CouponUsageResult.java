package dev.be.coupon.api.coupon.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponUsageResult(
        UUID userId,
        UUID couponId,
        boolean used,
        LocalDateTime usedAt
) {
    public static CouponUsageResult from(
            final UUID userId, final UUID couponId, final boolean used, final LocalDateTime usedAt) {
        return new CouponUsageResult(
                userId,
                couponId,
                used,
                usedAt
        );
    }
}
