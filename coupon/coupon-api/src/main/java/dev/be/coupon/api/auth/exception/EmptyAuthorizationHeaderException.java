package dev.be.coupon.api.auth.exception;

import dev.be.coupon.api.common.support.error.CouponException;
import dev.be.coupon.api.common.support.error.ErrorType;

public class EmptyAuthorizationHeaderException extends CouponException {

    public EmptyAuthorizationHeaderException() {
        super(ErrorType.AUTHORIZATION_HEADER_MISSING);
    }
}
