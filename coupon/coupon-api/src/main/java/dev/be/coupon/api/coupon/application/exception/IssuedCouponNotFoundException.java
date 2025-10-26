package dev.be.coupon.api.coupon.application.exception;

import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;

public class IssuedCouponNotFoundException extends CouponException {

    public IssuedCouponNotFoundException(final Object data) {
        super(ErrorType.ISSUED_COUPON_NOT_FOUND, data);
    }
}
