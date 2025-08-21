package dev.be.coupon.api.coupon.presentation.dto

import dev.be.coupon.api.coupon.application.dto.CouponCreateResult
import java.time.LocalDateTime
import java.util.*

data class CouponCreateResponse(
    val id: UUID,
    val name: String,
    val type: String,
    val totalQuantity: Int,
    val status: String,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime
) {
    companion object {
        fun from(result: CouponCreateResult): CouponCreateResponse {
            return CouponCreateResponse(
                id = result.id,
                name = result.name,
                type = result.type,
                totalQuantity = result.totalQuantity,
                status = result.status,
                validFrom = result.validFrom,
                validUntil = result.validUntil
            )
        }
    }
}
