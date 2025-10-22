package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateResponse(
        UUID id,
        String couponName,
        String couponType,
        String couponDiscountType,
        BigDecimal couponDiscountValue,
        int totalQuantity,
        String couponStatus,
        LocalDateTime expiredAt
) {
    public static CouponCreateResponse from(final CouponCreateResult result) {
        return new CouponCreateResponse(
                result.id(),
                result.couponName(),
                result.couponType(),
                result.couponDiscountType(),
                result.couponDiscountValue(),
                result.totalQuantity(),
                result.couponStatus(),
                result.expiredAt()
        );
    }
}
