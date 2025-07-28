package dev.be.coupon.api.coupon.presentation;

import dev.be.coupon.api.auth.presentation.AuthenticationPrincipal;
import dev.be.coupon.api.auth.presentation.dto.LoginUser;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateRequest;
import dev.be.coupon.api.coupon.presentation.dto.CouponCreateResponse;
import dev.be.coupon.api.coupon.presentation.dto.CouponUsageResponse;
import dev.be.coupon.common.support.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Tag(
        name = "쿠폰",
        description = "쿠폰과 관련된 그룹입니다."
)
public interface CouponControllerDocs {


    @Operation(
            summary = "쿠폰 생성",
            description = "관리자 권한이 있는 계정만 쿠폰을 생성할 수 있다."
    )
    @RequestBody(
            description = "쿠폰 생성 요청 예시",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "name": "치킨 할인 쿠폰",
                              "type": "CHICKEN",
                              "totalQuantity": 100,
                              "validFrom": "2025-08-01T00:00:00",
                              "validUntil": "2025-12-31T23:59:59"
                            }
                            """)
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "쿠폰 생성 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 없는 사용자", content = @Content)
    })
    ResponseEntity<CommonResponse<CouponCreateResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal final LoginUser loginUser,
            @RequestBody final CouponCreateRequest request
    );

    @Operation(
            summary = "쿠폰 발급 요청 (비동기)",
            description = """
                        비동기 방식으로 쿠폰 발급을 요청합니다.
                        
                        요청은 즉시 대기열에 등록되며, 실제 발급 결과는 별도로 처리됩니다.
                        
                        - 202 ACCEPTED: 발급 요청 성공 (선착순 안에 포함)
                        - 200 OK: 발급 불가 (쿠폰 소진 또는 중복 참여)
                        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "쿠폰 발급 요청 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "response": "쿠폰 발급 요청이 성공적으로 접수되었습니다.",
                                      "error": null
                                    }
                                    """))),
            @ApiResponse(responseCode = "200", description = "발급 불가 (쿠폰 소진 또는 중복 참여)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = {
                                    @ExampleObject(name = "쿠폰 소진", value = """
                                            {
                                              "success": true,
                                              "response": "아쉽지만, 쿠폰이 모두 소진되었습니다.",
                                              "error": null
                                            }
                                            """),
                                    @ExampleObject(name = "중복 참여", value = """
                                            {
                                              "success": true,
                                              "response": "이미 참여하셨습니다.",
                                              "error": null
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content)
    })
    ResponseEntity<CommonResponse<String>> issue(
            @Parameter(hidden = true) @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId
    );


    @Operation(
            summary = "쿠폰 사용",
            description = "사용자는 발급받은 쿠폰을 사용할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "쿠폰 사용 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content),
            @ApiResponse(responseCode = "404", description = "발급되지 않은 쿠폰", content = @Content)
    })
    ResponseEntity<CommonResponse<CouponUsageResponse>> usage(
            @Parameter(hidden = true) @AuthenticationPrincipal final LoginUser loginUser,
            @PathVariable final UUID couponId
    );
}
