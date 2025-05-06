package dev.be.coupon.api.coupon.presentation.dto;

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand;

import java.time.LocalDateTime;

public record CouponCreateRequest(
        String name,
        String type,
        int totalQuantity,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public CouponCreateCommand toCommand() {
        return new CouponCreateCommand(name, type, totalQuantity, validFrom, validUntil);
    }
}
