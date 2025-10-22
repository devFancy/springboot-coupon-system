package dev.be.coupon.api.auth.presentation;

import dev.be.coupon.api.auth.presentation.dto.request.AuthLoginRequest;
import dev.be.coupon.api.auth.presentation.dto.response.AuthLoginResponse;
import dev.be.coupon.common.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "인증",
        description = """
                    인증과 관련된 그룹입니다.
                    
                    로그인 기능을 제공합니다.
                """
)
public interface AuthControllerDocs {

    @Operation(
            summary = "로그인 성공",
            description = "아이디와 비밀번호를 기반으로 로그인을 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
    })
    ResponseEntity<ApiResponse<AuthLoginResponse>> login(
            @RequestBody AuthLoginRequest request);
}
