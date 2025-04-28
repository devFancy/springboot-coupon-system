package dev.be.coupon.api.user.domain;

import dev.be.coupon.api.user.domain.vo.Password;
import dev.be.coupon.api.user.domain.vo.Username;
import dev.be.coupon.api.user.exception.InvalidUserException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @DisplayName("사용자의 이름과 비밀번호를 입력하면 사용자가 생성된다.")
    @Test
    void create_user() {
        // given
        String username = "username1";
        String password = "password1";

        // when
        User user = new User(username, password);

        // then
        assertThat(user.getId()).isNotNull();
        assertEquals(user.getUsername(), username);
        assertEquals(user.getPassword(), password);
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
        assertThatThrownBy(() -> new Password(invalidPassword))
                .isInstanceOf(InvalidUserException.class)
                .hasMessage("사용자 관련 부분에서 예외가 발생했습니다.");
    }
}
