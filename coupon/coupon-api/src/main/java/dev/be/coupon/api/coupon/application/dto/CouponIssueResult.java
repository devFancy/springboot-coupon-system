package dev.be.coupon.api.coupon.application.dto;

import dev.be.coupon.api.coupon.domain.IssuedCoupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueResult(
        UUID userId,
        UUID couponId,
        boolean used,
        LocalDateTime issuedAt
) {
    public static CouponIssueResult from(final IssuedCoupon issuedCoupon) {
        return new CouponIssueResult(
                issuedCoupon.getUserId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.isUsed(),
                issuedCoupon.getIssuedAt()
        );
    }
}
