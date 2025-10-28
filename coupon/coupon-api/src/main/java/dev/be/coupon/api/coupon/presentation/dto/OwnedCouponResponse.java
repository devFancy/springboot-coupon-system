package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.OwnedCouponFindResult;
import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OwnedCouponResponse(
        // IssuedCoupon
        UUID issuedCouponId,
        UUID userId,
        boolean used,
        LocalDateTime issuedAt,
        LocalDateTime usedAt,

        // Coupon
        UUID couponId,
        String couponName,
        CouponType couponType,
        CouponDiscountType couponDiscountType,
        BigDecimal couponDiscountValue,
        CouponStatus couponStatus
) {
    public static OwnedCouponResponse from(final OwnedCouponFindResult result) {
        return new OwnedCouponResponse(
                result.issuedCouponId(),
                result.userId(),
                result.used(),
                result.issuedAt(),
                result.usedAt(),
                result.couponId(),
                result.couponName(),
                result.couponType(),
                result.couponDiscountType(),
                result.couponDiscountValue(),
                result.couponStatus()
        );
    }
}
