package dev.be.coupon.domain.coupon.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class CouponNotCurrentlyUsableException extends CouponException {

    public CouponNotCurrentlyUsableException(final Object data) {
        super(ErrorType.INVALID_COUPON_NOT_CURRENTLY_USABLE, data);
    }
}
