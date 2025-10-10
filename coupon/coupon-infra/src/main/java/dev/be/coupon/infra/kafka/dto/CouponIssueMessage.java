package dev.be.coupon.infra.kafka.dto;

import java.util.UUID;

public record CouponIssueMessage(UUID userId, UUID couponId, UUID failedIssuedCouponId) {
}
