package dev.be.coupon.api.user.infrastructure;

import dev.be.coupon.domain.user.vo.PasswordHasher;

/**
 * 테스트용 Fake PasswordHasher 구현체입니다.
 * <p>
 * 실제 BCrypt 해시가 아닌 단순 문자열 조작을 통해 암호화/비교를 수행하여
 * 테스트 속도를 높이고 예측 가능한 동작을 제공합니다.
 */
public class FakePasswordHasherClient implements PasswordHasher {

    @Override
    public String encrypt(final String password) {
        return "hashed_" + password;
    }

    @Override
    public boolean matches(final String password, final String encryptedPassword) {
        return encryptedPassword.equals("hashed_" + password);
    }
}
