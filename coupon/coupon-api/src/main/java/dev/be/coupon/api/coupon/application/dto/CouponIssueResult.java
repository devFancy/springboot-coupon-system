package dev.be.coupon.api.coupon.application.dto;


import dev.be.coupon.domain.coupon.IssuedCoupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueResult(
        UUID userId,
        UUID couponId,
        boolean used,
        boolean alreadyIssued,
        boolean quantityExceeded,
        LocalDateTime issuedAt
) {

    // 동기 저장 시 사용
    public static CouponIssueResult from(final IssuedCoupon issuedCoupon) {
        return new CouponIssueResult(
                issuedCoupon.getUserId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.isUsed(),
                false,
                false,
                issuedCoupon.getIssuedAt()
        );
    }
}
