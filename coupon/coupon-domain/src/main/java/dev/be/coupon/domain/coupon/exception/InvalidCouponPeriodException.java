package dev.be.coupon.domain.coupon.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidCouponPeriodException extends CouponException {

    public InvalidCouponPeriodException(final Object data) {
        super(ErrorType.INVALID_COUPON_PERIOD, data);
    }
}
