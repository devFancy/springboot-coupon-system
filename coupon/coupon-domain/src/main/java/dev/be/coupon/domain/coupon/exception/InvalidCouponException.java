package dev.be.coupon.domain.coupon.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidCouponException extends CouponException {

    public InvalidCouponException() {
        super(ErrorType.INVALID_COUPON);
    }

    public InvalidCouponException(final Object data) {
        super(ErrorType.INVALID_COUPON, data);
    }
}
