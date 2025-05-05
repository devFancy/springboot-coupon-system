package dev.be.coupon.api.coupon.presentation.dto;

import java.time.LocalDateTime;

public record CouponCreateRequest(
        String name,
        String type,
        int totalQuantity,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
}
