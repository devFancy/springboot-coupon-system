package dev.be.coupon.api.coupon.domain.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class InvalidCouponPeriodException extends CouponException {

    public InvalidCouponPeriodException(final Object data) {
        super(ErrorType.INVALID_COUPON_PERIOD, data);
    }
}
