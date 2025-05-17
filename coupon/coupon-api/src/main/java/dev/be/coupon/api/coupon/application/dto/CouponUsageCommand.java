package dev.be.coupon.api.coupon.application.dto;

import java.util.UUID;

public record CouponUsageCommand(
        UUID userId,
        UUID couponId
) {
}
