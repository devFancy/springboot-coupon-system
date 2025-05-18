package dev.be.coupon.domain.user.vo;

import dev.be.coupon.domain.user.exception.InvalidUserException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import static java.util.Objects.isNull;

import java.util.Objects;

@Embeddable
public class Username {

    @Column(name = "username", nullable = false)
    private String username;

    protected Username() {
    }

    public Username(final String username) {
        validate(username);
        this.username = username;
    }

    private void validate(final  String value) {
        if (isNull(value) || value.isBlank()) {
            throw new InvalidUserException("사용자의 이름이 존재해야 합니다.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Username username1)) return false;
        return Objects.equals(getUsername(), username1.getUsername());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername());
    }

    public String getUsername() {
        return username;
    }
}
