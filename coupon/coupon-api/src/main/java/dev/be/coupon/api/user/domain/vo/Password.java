package dev.be.coupon.api.user.domain.vo;

import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import static java.util.Objects.isNull;

import java.util.Objects;


@Embeddable
public class Password {

    @Column(name = "password", nullable = false)
    private String password;

    protected Password() {
    }

    public Password(final String password) {
        validate(password);
        this.password = password;
    }

    private String validate(final  String value) {
        if (isNull(value) || value.isBlank()) {
            throw new InvalidUserException("사용자의 비밀번호가 존재해야 합니다.");
        }
        return value;
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
