package dev.be.coupon.api.user.presentation;

import dev.be.coupon.api.common.support.response.CommonResponse;
import dev.be.coupon.api.user.presentation.dto.UserLoginRequest;
import dev.be.coupon.api.user.presentation.dto.UserLoginResponse;
import dev.be.coupon.api.user.presentation.dto.UserSignUpRequest;
import dev.be.coupon.api.user.presentation.dto.UserSignUpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "사용자",
        description = """
                    사용자과 관련된 그룹입니다.
                    
                    회원가입, 로그인 기능을 제공합니다.
                """
)
public interface UserControllerDocs {

    @Operation(
            summary = "회원가입 성공",
            description = "아이디와 비밀번호를 기반으로 회원가입을 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    })
    ResponseEntity<CommonResponse<UserSignUpResponse>> signUp(
            @RequestBody final UserSignUpRequest request
    );

    @Operation(
            summary = "로그인 성공",
            description = "아이디와 비밀번호를 기반으로 로그인을 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
    })
    ResponseEntity<CommonResponse<UserLoginResponse>> login(
            @RequestBody final UserLoginRequest request
    );
}
