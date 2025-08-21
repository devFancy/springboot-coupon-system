package dev.be.coupon.api.coupon.application.dto

import java.util.UUID

data class CouponUsageCommand(
    val userId: UUID,
    val couponId: UUID
)
