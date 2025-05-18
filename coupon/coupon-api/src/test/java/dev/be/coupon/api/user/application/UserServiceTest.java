package dev.be.coupon.api.user.application;

import dev.be.coupon.api.user.application.dto.UserSignUpCommand;
import dev.be.coupon.api.user.application.dto.UserSignUpResult;
import dev.be.coupon.domain.user.UserRepository;
import dev.be.coupon.domain.user.exception.InvalidUserException;
import dev.be.coupon.domain.user.vo.PasswordHasher;
import dev.be.coupon.api.user.infrastructure.FakePasswordHasherClient;
import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThat;
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
}
