package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponIssueResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueResponse(
        UUID userId,
        UUID couponId,
        boolean used,
        LocalDateTime issuedAt
) {
    public static CouponIssueResponse from(final CouponIssueResult result) {
        return new CouponIssueResponse(
                result.userId(),
                result.couponId(),
                result.used(),
                result.issuedAt()
        );
    }
}
