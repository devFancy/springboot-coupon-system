package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponCreateRequest(
        String couponName,
        CouponType couponType,
        CouponDiscountType couponDiscountType,
        BigDecimal couponDiscountValue,
        int totalQuantity,
        LocalDateTime expiredAt
) {
}
