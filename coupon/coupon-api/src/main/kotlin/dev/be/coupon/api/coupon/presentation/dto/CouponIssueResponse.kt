package dev.be.coupon.api.coupon.presentation.dto

import dev.be.coupon.api.coupon.application.dto.CouponIssueResult
import java.time.LocalDateTime
import java.util.*

data class CouponIssueResponse(
    val userId: UUID,
    val couponId: UUID,
    val used: Boolean,
    val issuedAt: LocalDateTime
) {
    companion object {
        fun from(result: CouponIssueResult): CouponIssueResponse {
            return CouponIssueResponse(
                userId = result.userId,
                couponId = result.couponId,
                used = result.used,
                issuedAt = result.issuedAt
            )
        }
    }
}


