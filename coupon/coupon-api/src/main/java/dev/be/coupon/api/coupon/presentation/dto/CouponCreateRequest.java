package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponCreateRequest(
        String name,
        String type,
        int totalQuantity,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public CouponCreateCommand toCommand(UUID userId) {
        return new CouponCreateCommand(userId, name, type, totalQuantity, validFrom, validUntil);
    }
}
