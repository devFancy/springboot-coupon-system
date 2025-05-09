package dev.be.coupon.api.coupon.infrastructure.kafka.dto;

import java.util.UUID;

public record CouponIssueMessage(UUID userId, UUID couponId) {
}
