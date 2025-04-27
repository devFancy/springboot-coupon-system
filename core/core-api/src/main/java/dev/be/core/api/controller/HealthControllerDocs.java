package dev.be.core.api.controller;

import dev.be.core.api.support.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(
        name = "Health Check"
)
public interface HealthControllerDocs {

    @Operation(
            summary = "헬스체크",
            description = "서버가 정상 작동 중인지 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 접근", content = @Content(schema = @Schema(hidden = true)))
    })
    ResponseEntity<CommonResponse<?>> health();
}
