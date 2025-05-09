package dev.be.coupon.api.user.domain.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class InvalidUserException extends CouponException {
    public InvalidUserException() {
        super(ErrorType.INVALID_USER);
    }

    public InvalidUserException(final Object data) {
        super(ErrorType.INVALID_USER, data);
    }
}
