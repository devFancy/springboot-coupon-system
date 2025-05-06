package dev.be.coupon.api.coupon.application.dto;

import dev.be.coupon.api.coupon.domain.Coupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateResult(
        UUID id,
        String name,
        String type,
        int totalQuantity,
        String status,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public static CouponCreateResult from(final Coupon coupon) {
        return new CouponCreateResult(
                coupon.getId(),
                coupon.getCouponName().getName(),
                coupon.getCouponType().name(),
                coupon.getTotalQuantity(),
                coupon.getCouponStatus().name(),
                coupon.getValidFrom(),
                coupon.getValidUntil()
        );
    }
}
