package dev.be.coupon.api.coupon.application.dto;


import dev.be.coupon.domain.coupon.CouponDiscountType;
import dev.be.coupon.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateCommand(
        UUID userId,
        String couponName,
        CouponType couponType,
        CouponDiscountType couponDiscountType,
        BigDecimal couponDiscountValue,
        int totalQuantity,
        LocalDateTime expiredAt
) {
}
