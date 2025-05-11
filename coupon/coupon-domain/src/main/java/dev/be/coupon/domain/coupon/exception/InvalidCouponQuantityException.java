package dev.be.coupon.domain.coupon.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidCouponQuantityException extends CouponException {

    public InvalidCouponQuantityException(final Object data) {
        super(ErrorType.INVALID_COUPON_QUANTITY, data);
    }
}
