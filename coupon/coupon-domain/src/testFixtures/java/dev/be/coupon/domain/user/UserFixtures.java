package dev.be.coupon.domain.user;

import dev.be.coupon.domain.user.vo.PasswordHasher;

public class UserFixtures {

    public static final String 사용자_이름 = "fancy";
    public static final String 사용자_비밀번호 = "fancy1234";

    public static User 사용자(final PasswordHasher passwordHasher) {
        return new User(
                사용자_이름,
                사용자_비밀번호,
                passwordHasher
        );
    }

    public static User 관리자(final PasswordHasher passwordHasher) {
        User user = new User(
                "admin",
                "admin1234",
                passwordHasher
        );
        user.assignAdminRole();
        return user;
    }

    public static User 사용자(final String username, final String password, final PasswordHasher passwordHasher) {
        return new User(
                username,
                password,
                passwordHasher
        );
    }

    public static User 이름이_없는_사용자(final PasswordHasher passwordHasher) {
        return new User(
                null,
                사용자_비밀번호,
                passwordHasher
        );
    }
}
