package dev.be.coupon.api.coupon.application.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class CouponNotFoundException extends CouponException {

    public CouponNotFoundException(final Object data) {
        super(ErrorType.NOTFOUND_COUPON, data);
    }
}
