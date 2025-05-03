package dev.be.coupon.api.coupon.domain.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class InvalidCouponException extends CouponException {

    public InvalidCouponException() {
        super(ErrorType.INVALID_COUPON);
    }

    public InvalidCouponException(final Object data) {
        super(ErrorType.INVALID_COUPON, data);
    }
}
