package dev.be.coupon.domain.user.vo;

import dev.be.coupon.domain.user.exception.UserDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import static java.util.Objects.isNull;

import java.util.Objects;

/**
 * 비밀번호의 암호화 및 검증 책임
 * 비밀번호는 절대로 복호화할 수 없도록 BCrypt 같은 단방향 해시 알고리즘으로 저장해야 한다.
 * `BCrypt` 는 Blowfish 기반의 암호화 해시 함수
 * - 단방향 해시이며, 같은 비밀번호라도 매번 다른 해시값이 생성되도록 salt를 내부적으로 포함한다.
 * - 반복 횟수(cost factor)를 조절하여 연산 복잡도를 높일 수 있어 무차별 대입 공격에 강하다.
 */
@Embeddable
public class Password {

    @Column(name = "password", nullable = false)
    private String password;

    protected Password() {
    }

    // 회원가입
    public Password(final String password, final PasswordHasher encryptor) {
        validate(password);
        this.password = encryptor.encrypt(password);
    }

    // 비밀번호가 일치하는지 확인
    public boolean matches(final String rawPassword, final PasswordHasher encryptor) {
        validate(rawPassword);
        return encryptor.matches(rawPassword, this.password); // 원문 비밀번호와 암호화된 비밀번호 비교
    }


    private void validate(final String value) {
        if (isNull(value) || value.isBlank()) {
            throw new UserDomainException("사용자의 비밀번호가 존재해야 합니다.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password password1)) return false;
        return Objects.equals(password, password1.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(password);
    }

    public String getPassword() {
        return password;
    }
}
