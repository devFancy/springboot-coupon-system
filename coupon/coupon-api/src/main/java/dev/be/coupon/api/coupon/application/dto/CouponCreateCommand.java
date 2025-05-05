package dev.be.coupon.api.coupon.application.dto;

import java.time.LocalDateTime;

public record CouponCreateCommand(
        String name,
        String type,
        int totalQuantity,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
}
