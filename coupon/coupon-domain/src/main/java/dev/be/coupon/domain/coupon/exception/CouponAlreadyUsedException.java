package dev.be.coupon.domain.coupon.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class CouponAlreadyUsedException extends CouponException {

    public CouponAlreadyUsedException(final Object data) {
        super(ErrorType.INVALID_COUPON_ALREADY_USED, data);
    }
}
