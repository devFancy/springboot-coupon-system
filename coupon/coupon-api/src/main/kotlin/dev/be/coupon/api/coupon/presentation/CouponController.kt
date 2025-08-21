package dev.be.coupon.api.coupon.presentation

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal
import dev.be.coupon.api.auth.presentation.dto.LoginUser
import dev.be.coupon.api.coupon.application.CouponService
import dev.be.coupon.api.coupon.application.dto.CouponIssueCommand
import dev.be.coupon.api.coupon.application.dto.CouponUsageCommand
import dev.be.coupon.api.coupon.presentation.dto.*
import dev.be.coupon.common.support.response.CommonResponse
import dev.be.coupon.domain.coupon.CouponIssueRequestResult
import dev.be.coupon.domain.coupon.exception.UnauthorizedAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.*

@RequestMapping("/api/coupon")
@RestController
class CouponController (
    private val couponService: CouponService
) : CouponControllerDocs {

    @PostMapping
    override fun create(
        @AuthenticationPrincipal loginUser: LoginUser,
        @RequestBody request: CouponCreateRequest
    ): ResponseEntity<CommonResponse<CouponCreateResponse>> {
        val userId = loginUser.id ?: throw UnauthorizedAccessException("로그인된 사용자만 쿠폰을 생성할 수 있습니다.")

        val result = couponService.create(request.toCommand(userId))
        return ResponseEntity.created(URI.create("/api/coupon/${result.id}"))
            .body(CommonResponse.success(CouponCreateResponse.from(result)))
    }

    @PostMapping("/{couponId}/issue")
    override fun issue(
        @AuthenticationPrincipal loginUser: LoginUser,
        @PathVariable("couponId") couponId: UUID
    ): ResponseEntity<CommonResponse<String>> {
        val userId = loginUser.id ?: throw UnauthorizedAccessException("로그인된 사용자만 쿠폰을 생성할 수 있습니다.")

        val command = CouponIssueCommand(userId, couponId)
        return getCommonResponseResponseEntity(command)
    }



    @PostMapping("/{couponId}/issue/test")
    fun issue(
        @RequestBody request: CouponIssueRequest,
        @PathVariable("couponId") couponId: UUID
    ): ResponseEntity<CommonResponse<String>> {

        val command = CouponIssueCommand(request.userId, couponId)
        return getCommonResponseResponseEntity(command)
    }

    @PostMapping("/{couponId}/usage")
    override fun usage(
        @AuthenticationPrincipal loginUser: LoginUser?,
        @PathVariable couponId: UUID
    ): ResponseEntity<CommonResponse<CouponUsageResponse>> {

        val userId = loginUser?.id ?: throw UnauthorizedAccessException("로그인된 사용자만 쿠폰을 사용할 수 있습니다.")

        val command = CouponUsageCommand(userId, couponId)
        val result = couponService.usage(command)
        return ResponseEntity.ok().body(CommonResponse.success(CouponUsageResponse.from(result)))
    }


    private fun getCommonResponseResponseEntity(command: CouponIssueCommand): ResponseEntity<CommonResponse<String>> {
        val result = couponService.issue(command)

        return when (result) {
            CouponIssueRequestResult.SUCCESS -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success("쿠폰 발급 요청이 성공적으로 접수되었습니다."))
            CouponIssueRequestResult.SOLD_OUT -> ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success("아쉽지만, 쿠폰이 모두 소진되었습니다."))
            CouponIssueRequestResult.DUPLICATE -> ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success("이미 참여하셨습니다."))
            null -> throw IllegalStateException("쿠폰 발급 결과가 null일 수 없습니다.")
        }
    }
}
