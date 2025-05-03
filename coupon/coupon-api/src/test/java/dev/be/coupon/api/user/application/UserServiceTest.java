package dev.be.coupon.api.user.application;

import dev.be.coupon.api.user.application.dto.UserLoginCommand;
import dev.be.coupon.api.user.application.dto.UserLoginResult;
import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.application.dto.UserSignUpResult;
import dev.be.coupon.api.user.domain.UserRepository;
import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import dev.be.coupon.api.user.domain.vo.PasswordHasher;
import dev.be.coupon.api.user.infrastructure.FakePasswordHasherClient;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private UserService userService;
    private UserRepository userRepository;
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordHasher = new FakePasswordHasherClient();
        userService = new UserService(userRepository, passwordHasher);
    }

    @DisplayName("아이디와 비밀번호를 입력하면 정상적으로 회원가입 성공한다.")
    @Test
    void success_signUp() {
        // given
        final UserSignUpCommand expected = new UserSignUpCommand("user1", "password1234");

        // when
        final UserSignUpResult actual = userService.signUp(expected);

        // then
        assertThat(actual.username()).isEqualTo(expected.username());
    }

    @DisplayName("같은 아이디로 회원가입을 시도하면 예외가 발생한다.")
    @Test
    void signUp_should_throw_exception_when_user_input_same_username() {
        // given
        final UserSignUpCommand first = new UserSignUpCommand("user1", "password1234");
        final UserSignUpCommand duplicate = new UserSignUpCommand("user1", "password1234");

        // when
        userService.signUp(first);

        // then
        Assertions.assertThatThrownBy(() -> userService.signUp(duplicate))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("아이디와 비밀번호를 입력하면 정상적으로 로그인을 성공한다.")
    @Test
    void success_login() {
        // given
        UserSignUpCommand expected = new UserSignUpCommand("user1", "password1234");
        userService.signUp(expected);

        UserLoginCommand actual = new UserLoginCommand("user1", "password1234");

        // when
        UserLoginResult result = userService.login(actual);

        // then
        assertNotNull(result);
        assertEquals(expected.username(), result.username());
    }

    @DisplayName("존재하지 않는 아이디와 비밀번호를 입력하면 예외가 발생한다.")
    @Test
    void login_should_throw_exception_when_user_not_found() {
        // given
        UserLoginCommand expected = new UserLoginCommand("nonexistentUser", "password1234");

        // when & then
        assertThatThrownBy(() -> userService.login(expected))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");

    }

    @DisplayName("로그인할 때 비밀번호가 일치하지 않으면 예외가 발생한다.")
    @Test
    void login_should_throw_exception_when_password_is_incorrect() {
        // given
        UserSignUpCommand expected = new UserSignUpCommand("user1", "password1234");
        userService.signUp(expected);

        UserLoginCommand actual = new UserLoginCommand("user1", "wrongPassword");
        assertThatThrownBy(() -> userService.login(actual))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");
        assertNotEquals(actual.password(), expected.password());
    }
}
