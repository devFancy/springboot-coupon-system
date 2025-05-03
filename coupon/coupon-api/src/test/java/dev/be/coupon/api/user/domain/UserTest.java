package dev.be.coupon.api.user.domain;

import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import dev.be.coupon.api.user.domain.vo.Password;
import dev.be.coupon.api.user.domain.vo.PasswordHasher;
import dev.be.coupon.api.user.domain.vo.Username;
import dev.be.coupon.api.user.infrastructure.BCryptPasswordHasher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        passwordHasher = new BCryptPasswordHasher(encoder);
    }

    @DisplayName("사용자의 이름과 비밀번호를 입력하면 사용자가 생성된다.")
    @Test
    void create_user() {
        // given
        final String username = "username1";
        final String password = "password1";

        // when
        final User user = new User(username, password, passwordHasher);

        // then
        assertThat(user.getId()).isNotNull();
        assertEquals(user.getUsername(), username);
        assertThat(passwordHasher.matches(password, user.getPassword())).isTrue();
    }

    @DisplayName("사용자의 이름이 존재하지 않으면 안된다")
    @ParameterizedTest(name = "사용자의 이름: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "   "})
    void should_throw_exception_when_username_is_null_or_blank(final String invalidUserName) {
        // given & when & then
        assertThatThrownBy(() -> new Username(invalidUserName))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("사용자의 비밀번호가 존재하지 않으면 안된다")
    @ParameterizedTest(name = "사용자의 비밀번호: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "   "})
    void should_throw_exception_when_password_is_null_or_blank(final String invalidPassword) {
        // given & when & then
        assertThatThrownBy(() -> new Password(invalidPassword, passwordHasher))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");
    }

    @DisplayName("원본 비밀번호와 해싱된 비밀번호가 일치하면 true 를 반환한다.")
    @Test
    void password_should_match_when_correct_password_given() {
        // given
        final String password = "password1234";
        final Password hashedPassword = new Password(password, passwordHasher);

        // when
        boolean matches = hashedPassword.matches(password, passwordHasher);

        // then
        assertThat(matches).isTrue();
    }

    @DisplayName("원본 비밀번호와 해싱된 비밀번호가 일치하지 않으면 false 를 반환한다.")
    @Test
    void password_should_not_match_when_wrong_password_given() {
        // given
        final String password = "password1234";
        final String wrongPassword = "wrongPassword";
        final Password hashedPassword = new Password(password, passwordHasher);

        // when
        final boolean matches = hashedPassword.matches(wrongPassword, passwordHasher);

        // then
        assertThat(matches).isFalse();
    }
}
