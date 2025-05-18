package dev.be.coupon.domain.coupon.exception;

import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class CouponNotIssuableException extends CouponException {

    public CouponNotIssuableException(final Object data) {
        super(ErrorType.INVALID_COUPON_ISSUABLE, data);
    }
}
