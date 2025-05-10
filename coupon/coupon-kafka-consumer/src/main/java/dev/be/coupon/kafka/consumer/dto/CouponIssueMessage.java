package dev.be.coupon.kafka.consumer.dto;

import java.util.UUID;

public record CouponIssueMessage(UUID userId, UUID couponId) {
}
