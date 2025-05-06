package dev.be.coupon.api.coupon.application.dto;

import java.util.UUID;

public record CouponIssueCommand(
        UUID userId,
        UUID couponId
) {
}
