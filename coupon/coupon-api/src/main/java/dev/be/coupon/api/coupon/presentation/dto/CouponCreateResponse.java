package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponCreateResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateResponse(
        UUID id,
        String name,
        String type,
        int totalQuantity,
        String status,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public static CouponCreateResponse from(final CouponCreateResult result) {
        return new CouponCreateResponse(
                result.id(),
                result.name(),
                result.type(),
                result.totalQuantity(),
                result.status(),
                result.validFrom(),
                result.validUntil()
        );
    }
}
