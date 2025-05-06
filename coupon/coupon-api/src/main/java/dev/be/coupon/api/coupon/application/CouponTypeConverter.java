package dev.be.coupon.api.coupon.application;

import deb.be.coupon.CouponType;
import dev.be.coupon.api.coupon.domain.exception.InvalidCouponTypeException;

public class CouponTypeConverter {

    public static CouponType from(final String type) {
        try {
            return CouponType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCouponTypeException(type);
        }
    }
}
