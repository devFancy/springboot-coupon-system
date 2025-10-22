package dev.be.coupon.api.coupon.application.dto;


import dev.be.coupon.domain.coupon.Coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateResult(
        UUID id,
        String couponName,
        String couponType,
        String couponDiscountType,
        BigDecimal couponDiscountValue,
        int totalQuantity,
        String couponStatus,
        LocalDateTime expiredAt
) {
    public static CouponCreateResult from(final Coupon coupon) {
        return new CouponCreateResult(
                coupon.getId(),
                coupon.getCouponName().getName(),
                coupon.getCouponType().name(),
                coupon.getCouponDiscountType().name(),
                coupon.getCouponDiscountValue(),
                coupon.getTotalQuantity(),
                coupon.getCouponStatus().name(),
                coupon.getExpiredAt()
        );
    }
}
