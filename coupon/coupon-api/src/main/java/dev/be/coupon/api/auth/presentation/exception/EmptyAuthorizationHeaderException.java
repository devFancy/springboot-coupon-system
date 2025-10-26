package dev.be.coupon.api.auth.presentation.exception;


import dev.be.coupon.api.support.error.CouponException;
import dev.be.coupon.api.support.error.ErrorType;

public class EmptyAuthorizationHeaderException extends CouponException {

    public EmptyAuthorizationHeaderException() {
        super(ErrorType.AUTH_HEADER_MISSING);
    }
}
