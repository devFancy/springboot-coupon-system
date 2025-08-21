package dev.be.coupon.api.coupon.application.dto

import dev.be.coupon.domain.coupon.Coupon
import dev.be.coupon.domain.coupon.CouponTypeConverter
import java.time.LocalDateTime
import java.util.*

data class CouponCreateCommand(
    val userId: UUID,
    val name: String,
    val type: String,
    val totalQuantity: Int,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime
) {
    fun toDomain(): Coupon {
        return Coupon(
            name,
            CouponTypeConverter.from(type),
            totalQuantity,
            validFrom,
            validUntil
        )
    }
}
