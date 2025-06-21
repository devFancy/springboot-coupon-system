package dev.be.coupon.domain.coupon.kafka.dto;

import java.util.UUID;

public record CouponIssueMessage(UUID userId, UUID couponId) {
}
