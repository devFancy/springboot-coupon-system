package dev.be.coupon.api.coupon.presentation.dto;

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
}
