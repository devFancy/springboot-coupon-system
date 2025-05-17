package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponUsageResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponUsageResponse(
        UUID userId,
        UUID couponId,
        boolean used,
        LocalDateTime usedAt
) {
    public static CouponUsageResponse from(final CouponUsageResult result) {
        return new CouponUsageResponse(
                result.userId(),
                result.couponId(),
                result.used(),
                result.usedAt()
        );
    }
}
