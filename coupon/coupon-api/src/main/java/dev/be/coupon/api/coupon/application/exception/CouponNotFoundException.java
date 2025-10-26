package dev.be.coupon.api.coupon.application.exception;


import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;

public class CouponNotFoundException extends CouponException {

    public CouponNotFoundException(final Object data) {
        super(ErrorType.COUPON_NOT_FOUND, data);
    }
}
