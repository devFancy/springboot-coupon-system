package dev.be.coupon.api.user.presentation;

import dev.be.coupon.api.support.response.ApiResponse;
import dev.be.coupon.api.user.application.UserService;
import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.application.dto.UserSignUpResult;
import dev.be.coupon.api.user.presentation.dto.UserSignUpRequest;
import dev.be.coupon.api.user.presentation.dto.UserSignUpResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RequestMapping("/api")
@RestController
public class UserController implements UserControllerDocs {

    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @PostMapping(value = "/users/signup")
    public ResponseEntity<ApiResponse<UserSignUpResponse>> signUp(@Valid @RequestBody final UserSignUpRequest request) {
        UserSignUpCommand command = new UserSignUpCommand(request.username(), request.password());
        UserSignUpResult result = userService.signUp(command);
        UserSignUpResponse response = new UserSignUpResponse(result.id(), result.username(), result.role());

        return ResponseEntity.created(URI.create("/api/users/signup/" + response.id()))
                .body(ApiResponse.success(response));
    }
}
