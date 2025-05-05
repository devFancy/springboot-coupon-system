package dev.be.coupon.api.coupon.application;

import deb.be.coupon.CouponType;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponException;

public class CouponTypeConverter {

    public static CouponType from(final String type) {
        try {
            return CouponType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCouponException("올바르지 않은 쿠폰 타입입니다: " + type);
        }
    }
}
