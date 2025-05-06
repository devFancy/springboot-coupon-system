package dev.be.coupon.api.coupon.domain.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class UnauthorizedAccessException extends CouponException {

    public UnauthorizedAccessException(final Object data) {
        super(ErrorType.UNAUTHORIZED_ACCESS, data);
    }
}
