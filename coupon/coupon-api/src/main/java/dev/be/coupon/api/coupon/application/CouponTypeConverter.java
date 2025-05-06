package dev.be.coupon.api.coupon.application;

import deb.be.coupon.CouponType;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;

public class CouponTypeConverter {

    public static CouponType from(final String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidCouponTypeException("쿠폰 타입이 null이거나 비어있습니다.");
        }

        try {
            return CouponType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCouponTypeException(type);
        }
    }
}
