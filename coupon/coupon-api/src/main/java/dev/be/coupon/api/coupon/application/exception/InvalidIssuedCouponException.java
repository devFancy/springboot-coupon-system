package dev.be.coupon.api.coupon.application.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class InvalidIssuedCouponException extends CouponException {

    public InvalidIssuedCouponException(final Object data) {
        super(ErrorType.INVALID_ISSUED_COUPON, data);
    }
}
