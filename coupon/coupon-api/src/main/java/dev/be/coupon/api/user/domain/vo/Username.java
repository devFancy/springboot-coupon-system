package dev.be.coupon.api.user.domain.vo;

import dev.be.coupon.api.user.domain.exception.InvalidUserException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import static java.util.Objects.isNull;

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

    public String getUsername() {
        return username;
    }
}
