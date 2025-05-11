package dev.be.coupon.api.coupon.application.dto;


import dev.be.coupon.domain.coupon.Coupon;
import dev.be.coupon.domain.coupon.CouponTypeConverter;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateCommand(
        UUID userId,
        String name,
        String type,
        int totalQuantity,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public Coupon toDomain() {
        return new Coupon(
                name,
                CouponTypeConverter.from(type),
                totalQuantity,
                validFrom,
                validUntil
        );
    }
}
