package dev.be.coupon.domain.coupon.vo;

import dev.be.coupon.domain.coupon.exception.CouponDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import static java.util.Objects.isNull;

import java.util.Objects;

@Embeddable
public class CouponName {

    @Column(name = "name", nullable = false)
    private String name;

    protected CouponName() {
    }

    public CouponName(final String name) {
        validate(name);
        this.name = name;
    }

    private void validate(final  String value) {
        if (isNull(value) || value.isBlank()) {
            throw new CouponDomainException("쿠폰 이름이 존재해야 합니다.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CouponName that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public String getName() {
        return name;
    }
}
