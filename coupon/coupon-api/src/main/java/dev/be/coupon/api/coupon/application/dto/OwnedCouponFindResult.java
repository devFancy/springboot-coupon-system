package dev.be.coupon.api.coupon.application.dto;

import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponStatus;
import dev.be.coupon.domain.coupon.CouponType;
import dev.be.coupon.domain.coupon.IssuedCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OwnedCouponFindResult(
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
    public static OwnedCouponFindResult of(final IssuedCoupon issuedCoupon, final Coupon coupon) {
        return new OwnedCouponFindResult(
                issuedCoupon.getId(),
                issuedCoupon.getUserId(),
                issuedCoupon.isUsed(),
                issuedCoupon.getIssuedAt(),
                issuedCoupon.getUsedAt(),
                coupon.getId(),
                coupon.getCouponName().getName(),
                coupon.getCouponType(),
                coupon.getCouponDiscountType(),
                coupon.getCouponDiscountValue(),
                coupon.getCouponStatus()
        );
    }
}
