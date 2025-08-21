package dev.be.coupon.api.coupon.presentation.dto

import dev.be.coupon.api.coupon.application.dto.CouponCreateCommand
import java.time.LocalDateTime
import java.util.*

data class CouponCreateRequest(
    val name: String,
    val type: String,
    val totalQuantity: Int,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime
)

fun CouponCreateRequest.toCommand(userId: UUID): CouponCreateCommand {
    return CouponCreateCommand(userId, name, type, totalQuantity, validFrom, validUntil)
}
