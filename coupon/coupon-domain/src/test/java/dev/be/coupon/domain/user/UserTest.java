package dev.be.coupon.domain.user;

import dev.be.coupon.domain.user.exception.UserDomainException;
import dev.be.coupon.domain.user.infrastructure.FakePasswordHasherClient;
import dev.be.coupon.domain.user.vo.Password;
import dev.be.coupon.domain.user.vo.PasswordHasher;
import dev.be.coupon.domain.user.vo.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.be.coupon.domain.user.UserFixtures.관리자;
import static dev.be.coupon.domain.user.UserFixtures.사용자;
import static dev.be.coupon.domain.user.UserFixtures.이름이_없는_사용자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new FakePasswordHasherClient();
    }

    @DisplayName("사용자의 이름과 비밀번호를 입력하면 사용자가 생성된다.")
    @Test
    void create_user() {
        // given
        final String username = "username1";
        final String password = "password1";

        // when
        final User user = 사용자(username, password, passwordHasher);

        // then
        assertThat(user.getId()).isNotNull();
        assertEquals(user.getUsername(), username);
        assertThat(passwordHasher.matches(password, user.getPassword())).isTrue();
    }

    @DisplayName("관리자 권한을 부여하면 UserRole이 ADMIN이 된다.")
    @Test
    void create_admin_user() {
        // given & when
        User admin = 관리자(passwordHasher);

        // then
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
    }

    @DisplayName("사용자의 이름이 존재하지 않으면 예외가 발생한다.")
    @ParameterizedTest(name = "사용자의 이름: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "   "})
    void should_throw_exception_when_username_is_null_or_blank(final String invalidUserName) {
        // given & when & then
        assertThatThrownBy(() -> new Username(invalidUserName))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("사용자의 이름이 존재해야 합니다.");
    }

    @DisplayName("사용자 생성 시 이름이 없으면 예외가 발생한다.")
    @Test
    void fail_create_user_invalid_name() {
        // given & when & then
        assertThatThrownBy(() -> 이름이_없는_사용자(passwordHasher))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("사용자의 이름이 존재해야 합니다.");
    }

    @DisplayName("사용자의 비밀번호가 존재하지 않으면 예외가 발생한다.")
    @ParameterizedTest(name = "사용자의 비밀번호: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "   "})
    void should_throw_exception_when_password_is_null_or_blank(final String invalidPassword) {
        // given & when & then
        assertThatThrownBy(() -> new Password(invalidPassword, passwordHasher))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("사용자의 비밀번호가 존재해야 합니다.");
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
