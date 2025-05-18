package dev.be.coupon.domain.user.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidUserException extends CouponException {
    public InvalidUserException() {
        super(ErrorType.INVALID_USER);
    }

    public InvalidUserException(final Object data) {
        super(ErrorType.INVALID_USER, data);
    }
}
