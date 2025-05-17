package dev.be.coupon.api.coupon.application.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class IssuedCouponNotFoundException extends CouponException {

    public IssuedCouponNotFoundException(final Object data) {
        super(ErrorType.NOTFOUND_ISSUED_COUPON, data);
    }
}
