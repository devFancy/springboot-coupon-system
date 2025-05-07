package dev.be.coupon.api.coupon.application.dto;

import dev.be.coupon.api.coupon.application.CouponTypeConverter;
import dev.be.coupon.api.coupon.domain.Coupon;

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
