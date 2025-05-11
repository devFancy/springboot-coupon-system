package dev.be.coupon.api.auth.presentation;


import dev.be.coupon.api.auth.application.AuthService;
import dev.be.coupon.api.auth.application.dto.AuthLoginCommand;
import dev.be.coupon.api.auth.application.dto.AuthLoginResult;
import dev.be.coupon.api.auth.presentation.dto.request.AuthLoginRequest;
import dev.be.coupon.api.auth.presentation.dto.response.AuthLoginResponse;
import dev.be.coupon.common.support.response.CommonResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/auth/login")
    public ResponseEntity<CommonResponse<AuthLoginResponse>> login(@Valid @RequestBody final AuthLoginRequest request) {
        AuthLoginCommand command = new AuthLoginCommand(request.username(), request.password());
        AuthLoginResult result = authService.login(command);

        String accessToken = authService.generateAccessToken(result.id());
        AuthLoginResult finalResult = result.withAccessToken(accessToken);

        AuthLoginResponse response = new AuthLoginResponse(finalResult.id(), finalResult.username(), accessToken);
        return ResponseEntity.ok().body(CommonResponse.success(response));
    }
}
