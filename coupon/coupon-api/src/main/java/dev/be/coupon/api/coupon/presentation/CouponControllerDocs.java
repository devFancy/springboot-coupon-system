package dev.be.coupon.api.coupon.presentation;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponIssueResponse;
import dev.be.coupon.common.support.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(
        name = "쿠폰",
        description = """
                    쿠폰과 관련된 그룹입니다.
                    
                    쿠폰 생성, 쿠폰 발급, 쿠폰 사용 기능을 제공합니다.
                """
)
public interface CouponControllerDocs {

    @Operation(
            summary = "쿠폰 생성 성공",
            description = "관리자 권한이 있는 계정만 쿠폰을 생성할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "쿠폰 생성 성공"),
    })
    ResponseEntity<CommonResponse<CouponCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal final LoginUser loginUser,
            @RequestBody final CouponCreateRequest request
    );

    @Operation(
            summary = "쿠폰 발급 성공",
            description = "사용자는 1번만 쿠폰을 발급할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "쿠폰 발급 성공"),
    })
    ResponseEntity<CommonResponse<CouponIssueResponse>> issue(
            @Parameter(hidden = true) @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId
    );
}
