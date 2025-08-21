package dev.be.coupon.api.coupon.presentation.dto

import dev.be.coupon.api.coupon.application.dto.CouponUsageResult
import java.time.LocalDateTime
import java.util.*

data class CouponUsageResponse(
    val userId: UUID,
    val couponId: UUID,
    val used: Boolean,
    val usedAt: LocalDateTime
) {
    companion object {
        fun from(result: CouponUsageResult): CouponUsageResponse {
            return CouponUsageResponse(
                userId = result.userId,
                couponId = result.couponId,
                used = result.used,
                usedAt = result.usedAt
            )
        }
    }
}
