package dev.be.coupon.domain.coupon.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidCouponTypeException extends CouponException {

    public InvalidCouponTypeException(final Object data) {
        super(ErrorType.INVALID_COUPON_TYPE, data);
    }
}
