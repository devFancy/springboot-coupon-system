package dev.be.coupon.api.auth.application;

import dev.be.coupon.api.auth.application.dto.AuthLoginCommand;
import dev.be.coupon.api.auth.application.dto.AuthLoginResult;
import dev.be.coupon.api.user.application.InMemoryUserRepository;
import dev.be.coupon.api.user.application.UserService;
import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import dev.be.coupon.api.user.domain.vo.PasswordHasher;
import dev.be.coupon.api.user.infrastructure.FakePasswordHasherClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private UserService userService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = new InMemoryUserRepository();
        PasswordHasher passwordHasher = new FakePasswordHasherClient();
        TokenCreator tokenCreator = new FakeTokenCreator();

        this.userService = new UserService(userRepository, passwordHasher);
        this.authService = new AuthService(userRepository, passwordHasher, tokenCreator);
    }

    @DisplayName("아이디와 비밀번호를 입력하면 정상적으로 로그인을 성공한다.")
    @Test
    void success_login() {
        // given
        UserSignUpCommand signUpCommand = new UserSignUpCommand("user1", "password1234");
        userService.signUp(signUpCommand);

        AuthLoginCommand loginCommand = new AuthLoginCommand("user1", "password1234");

        // when
        AuthLoginResult result = authService.login(loginCommand);

        // then
        assertNotNull(result);
        assertEquals("user1", result.username());
    }

    @DisplayName("존재하지 않는 아이디와 비밀번호를 입력하면 예외가 발생한다.")
    @Test
    void login_should_throw_exception_when_user_not_found() {
        // given
        AuthLoginCommand loginCommand = new AuthLoginCommand("nonexistentUser", "password1234");

        // when & then
        assertThatThrownBy(() -> authService.login(loginCommand))
                .isInstanceOf(InvalidUserException.class)
                .hasMessageContaining("사용자 관련 부분에서 예외가 발생했습니다.");
    }

    @Test
    @DisplayName("로그인할 때 비밀번호가 일치하지 않으면 예외가 발생한다.")
    void login_should_throw_exception_when_password_is_incorrect() {
        // given
        userService.signUp(new UserSignUpCommand("user1", "password1234"));

        AuthLoginCommand loginCommand = new AuthLoginCommand("user1", "wrongPassword");

        // when & then
        assertThatThrownBy(() -> authService.login(loginCommand))
                .isInstanceOf(InvalidUserException.class)
                .hasMessageContaining("사용자 관련 부분에서 예외가 발생했습니다.");
    }
}

