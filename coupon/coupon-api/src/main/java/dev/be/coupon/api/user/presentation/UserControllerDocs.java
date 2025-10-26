package dev.be.coupon.api.user.presentation;

import dev.be.coupon.api.support.response.ApiResponse;
import dev.be.coupon.api.user.presentation.dto.UserSignUpRequest;
import dev.be.coupon.api.user.presentation.dto.UserSignUpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "사용자",
        description = """
                    사용자과 관련된 그룹입니다.
                    
                    회원가입 기능을 제공합니다.
                """
)
public interface UserControllerDocs {

    @Operation(
            summary = "회원가입 성공",
            description = "아이디와 비밀번호를 기반으로 회원가입을 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
    })
    ResponseEntity<ApiResponse<UserSignUpResponse>> signUp(
            @RequestBody final UserSignUpRequest request
    );
}
