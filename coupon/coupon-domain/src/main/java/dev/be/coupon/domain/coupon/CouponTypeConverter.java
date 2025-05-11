package dev.be.coupon.domain.coupon;

import dev.be.coupon.domain.coupon.exception.InvalidCouponTypeException;

public class CouponTypeConverter {

    public static CouponType from(final String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidCouponTypeException("쿠폰을 생성하려면 유효한 쿠폰 타입이 존재해야 합니다.");
        }

        try {
            return CouponType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCouponTypeException(type);
        }
    }
}
