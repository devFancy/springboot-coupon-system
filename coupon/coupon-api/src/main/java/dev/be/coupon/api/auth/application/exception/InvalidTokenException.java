package dev.be.coupon.api.auth.application.exception;


import dev.be.coupon.common.support.error.CouponException;
import dev.be.coupon.common.support.error.ErrorType;

public class InvalidTokenException extends CouponException {

    public InvalidTokenException(final Object data) {
        super(ErrorType.INVALID_AUTH, data);
    }
}
