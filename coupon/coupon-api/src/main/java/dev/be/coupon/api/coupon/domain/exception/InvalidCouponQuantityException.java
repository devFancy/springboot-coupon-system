package dev.be.coupon.api.coupon.domain.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class InvalidCouponQuantityException extends CouponException {

    public InvalidCouponQuantityException(final Object data) {
        super(ErrorType.INVALID_COUPON_QUANTITY, data);
    }
}
