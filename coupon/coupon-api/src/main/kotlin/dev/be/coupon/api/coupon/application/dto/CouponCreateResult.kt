package dev.be.coupon.api.coupon.application.dto

import dev.be.coupon.domain.coupon.Coupon
import java.time.LocalDateTime
import java.util.*

data class CouponCreateResult(
    val id: UUID,
    val name: String,
    val type: String,
    val totalQuantity: Int,
    val status: String,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime
) {
    companion object {
        fun from(coupon: Coupon): CouponCreateResult {
            return CouponCreateResult(
                id = coupon.id,
                name = coupon.couponName.name,
                type = coupon.couponType.name,
                totalQuantity = coupon.totalQuantity,
                status = coupon.couponStatus.name,
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil
            )
        }
    }
}
